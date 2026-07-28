import 'package:flutter/material.dart';

import '../../../../app/store_strings.dart';
import '../../domain/module_extensions.dart';
import '../../state/store_controller.dart';
import '../widgets/module_card.dart';

class StoreHomePage extends StatefulWidget {
  const StoreHomePage({super.key});
  @override
  State<StoreHomePage> createState() => _StoreHomePageState();
}

class _StoreHomePageState extends State<StoreHomePage> {
  final searchController = TextEditingController();

  @override
  void dispose() {
    searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final controller = StoreScope.read(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(strings.title),
        actions: [
          IconButton(
            tooltip: strings.refresh,
            onPressed: controller.refresh,
            icon: const Icon(Icons.refresh),
          ),
          PopupMenuButton<String>(
            onSelected: (value) {
              if (value == 'legacy') controller.gateway.openLegacyStore();
            },
            itemBuilder: (_) => [
              PopupMenuItem(value: 'legacy', child: Text(strings.legacy)),
            ],
          ),
        ],
      ),
      body: AnimatedBuilder(
        animation: controller,
        builder: (context, _) {
          if (controller.catalog.loading &&
              controller.catalog.catalog == null) {
            return const Center(child: CircularProgressIndicator());
          }
          final error = controller.catalog.error;
          if (error != null && controller.catalog.catalog == null) {
            return _ErrorState(message: error, onRetry: controller.refresh);
          }
          final modules = controller.visibleModules;
          return RefreshIndicator(
            onRefresh: controller.refresh,
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverToBoxAdapter(child: _Header(controller: controller)),
                if (controller.catalog.catalog?.offline == true)
                  SliverToBoxAdapter(
                    child: _OfflineBanner(text: strings.offline),
                  ),
                SliverToBoxAdapter(
                  child: _SearchAndFilters(
                    searchController: searchController,
                    controller: controller,
                  ),
                ),
                if (modules.isEmpty)
                  SliverFillRemaining(
                    hasScrollBody: false,
                    child: Center(child: Text(strings.empty)),
                  )
                else if (controller.filters.grid)
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(10, 0, 10, 24),
                    sliver: SliverGrid.builder(
                      gridDelegate:
                          const SliverGridDelegateWithMaxCrossAxisExtent(
                            maxCrossAxisExtent: 420,
                            mainAxisExtent: 270,
                            crossAxisSpacing: 4,
                            mainAxisSpacing: 4,
                          ),
                      itemCount: modules.length,
                      itemBuilder: (_, index) =>
                          ModuleCard(module: modules[index], compact: true),
                    ),
                  )
                else ...[
                  // 列表模式：按 storeCategory 分组渲染（按结果组织模块）
                  for (final group in groupModulesByStoreCategory(modules)) ...[
                    SliverToBoxAdapter(
                      child: _CategoryHeader(
                        title: group.key,
                        count: group.value.length,
                      ),
                    ),
                    SliverPadding(
                      padding: const EdgeInsets.fromLTRB(10, 0, 10, 16),
                      sliver: SliverList.builder(
                        itemCount: group.value.length,
                        itemBuilder: (_, index) =>
                            ModuleCard(module: group.value[index]),
                      ),
                    ),
                  ],
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({required this.controller});
  final StoreController controller;
  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final featured = controller.catalog.modules
        .where((module) => module.featured == true)
        .firstOrNull;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [
                  Theme.of(context).colorScheme.primary,
                  Theme.of(context).colorScheme.tertiary,
                ],
              ),
              borderRadius: BorderRadius.circular(26),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  strings.subtitle,
                  style: Theme.of(
                    context,
                  ).textTheme.titleLarge?.copyWith(color: Colors.white),
                ),
                const SizedBox(height: 8),
                Text(
                  featured == null
                      ? '${controller.catalog.modules.length} modules'
                      : '${featured.safeName} · ${featured.summary}',
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: Colors.white70),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _Metric(
                  label: strings.installed,
                  value: controller.installed.modules.length,
                  route: '/store/installed',
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _Metric(
                  label: strings.updates,
                  value: controller.updates.modules.length,
                  route: '/store/updates',
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _Metric(
                  label: strings.downloads,
                  value: controller.downloads.active.length,
                  route: '/store/downloads',
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Metric extends StatelessWidget {
  const _Metric({
    required this.label,
    required this.value,
    required this.route,
  });
  final String label;
  final int value;
  final String route;
  @override
  Widget build(BuildContext context) => Card(
    child: InkWell(
      onTap: () => Navigator.pushNamed(context, route),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Column(
          children: [
            Text('$value', style: Theme.of(context).textTheme.titleLarge),
            Text(
              label,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.labelSmall,
            ),
          ],
        ),
      ),
    ),
  );
}

class _SearchAndFilters extends StatelessWidget {
  const _SearchAndFilters({
    required this.searchController,
    required this.controller,
  });
  final TextEditingController searchController;
  final StoreController controller;

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final categories = {
      'all',
      ...controller.catalog.modules.map((module) => module.safeCategory),
    };
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
      child: Column(
        children: [
          SearchBar(
            controller: searchController,
            hintText: strings.searchHint,
            leading: const Icon(Icons.search),
            trailing: [
              if (searchController.text.isNotEmpty)
                IconButton(
                  onPressed: () {
                    searchController.clear();
                    controller.filters.setQueryDebounced('');
                  },
                  icon: const Icon(Icons.clear),
                ),
            ],
            onChanged: controller.filters.setQueryDebounced,
            onSubmitted: controller.filters.commitSearch,
          ),
          if (controller.filters.history.isNotEmpty &&
              searchController.text.isEmpty)
            Row(
              children: [
                Expanded(
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: controller.filters.history
                          .map(
                            (item) => Padding(
                              padding: const EdgeInsets.only(right: 6),
                              child: ActionChip(
                                label: Text(item),
                                onPressed: () {
                                  searchController.text = item;
                                  controller.filters.commitSearch(item);
                                },
                              ),
                            ),
                          )
                          .toList(),
                    ),
                  ),
                ),
                TextButton(
                  onPressed: controller.filters.clearHistory,
                  child: Text(strings.clear),
                ),
              ],
            ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: Row(
                    children: categories
                        .map(
                          (category) => Padding(
                            padding: const EdgeInsets.only(right: 6),
                            child: ChoiceChip(
                              label: Text(
                                category == 'all'
                                    ? strings.all
                                    : storeCategoryDisplayNameOf(category),
                              ),
                              selected: controller.filters.category == category,
                              onSelected: (_) =>
                                  controller.filters.setCategory(category),
                            ),
                          ),
                        )
                        .toList(),
                  ),
                ),
              ),
              IconButton(
                tooltip: strings.filters,
                onPressed: () => _showFilters(context, controller),
                icon: const Icon(Icons.tune),
              ),
              IconButton(
                onPressed: controller.filters.toggleGrid,
                icon: Icon(
                  controller.filters.grid ? Icons.view_list : Icons.grid_view,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  void _showFilters(BuildContext context, StoreController controller) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => _FilterSheet(controller: controller),
    );
  }
}

class _FilterSheet extends StatelessWidget {
  const _FilterSheet({required this.controller});
  final StoreController controller;
  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: controller.filters,
    builder: (context, _) => SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              StoreStrings.of(context).filters,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              initialValue: controller.filters.runtimeFilter,
              decoration: const InputDecoration(labelText: 'Runtime'),
              items:
                  const [
                        'all',
                        'flutter',
                        'web',
                        'asset',
                        'android',
                        'native_service',
                        'unity',
                      ]
                      .map(
                        (value) =>
                            DropdownMenuItem(value: value, child: Text(value)),
                      )
                      .toList(),
              onChanged: (value) =>
                  controller.filters.setRuntimeType(value ?? 'all'),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<InstallFilter>(
              initialValue: controller.filters.installFilter,
              decoration: const InputDecoration(labelText: 'State'),
              items: InstallFilter.values
                  .map(
                    (value) =>
                        DropdownMenuItem(value: value, child: Text(value.name)),
                  )
                  .toList(),
              onChanged: (value) => controller.filters.setInstallFilter(
                value ?? InstallFilter.all,
              ),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<SizeFilter>(
              initialValue: controller.filters.sizeFilter,
              decoration: const InputDecoration(labelText: 'Size'),
              items: SizeFilter.values
                  .map(
                    (value) =>
                        DropdownMenuItem(value: value, child: Text(value.name)),
                  )
                  .toList(),
              onChanged: (value) =>
                  controller.filters.setSizeFilter(value ?? SizeFilter.all),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<VersionFilter>(
              initialValue: controller.filters.versionFilter,
              decoration: const InputDecoration(labelText: 'Version'),
              items: VersionFilter.values
                  .map(
                    (value) =>
                        DropdownMenuItem(value: value, child: Text(value.name)),
                  )
                  .toList(),
              onChanged: (value) => controller.filters.setVersionFilter(
                value ?? VersionFilter.all,
              ),
            ),
            const SizedBox(height: 10),
            DropdownButtonFormField<StoreSort>(
              initialValue: controller.filters.sort,
              decoration: const InputDecoration(labelText: 'Sort'),
              items: StoreSort.values
                  .map(
                    (value) =>
                        DropdownMenuItem(value: value, child: Text(value.name)),
                  )
                  .toList(),
              onChanged: (value) =>
                  controller.filters.setSort(value ?? StoreSort.featured),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: controller.filters.resetAdvanced,
                  child: const Text('Reset'),
                ),
                FilledButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('Done'),
                ),
              ],
            ),
          ],
        ),
      ),
    ),
  );
}

class _OfflineBanner extends StatelessWidget {
  const _OfflineBanner({required this.text});
  final String text;
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(16, 0, 16, 10),
    padding: const EdgeInsets.all(10),
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.secondaryContainer,
      borderRadius: BorderRadius.circular(12),
    ),
    child: Row(
      children: [
        const Icon(Icons.cloud_off),
        const SizedBox(width: 8),
        Expanded(child: Text(text)),
      ],
    ),
  );
}

/// 分组标题：显示 storeCategory 中文名与该组模块数量。
class _CategoryHeader extends StatelessWidget {
  const _CategoryHeader({required this.title, required this.count});
  final String title;
  final int count;
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            width: 4,
            height: 18,
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: theme.colorScheme.primary,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          Text(
            title,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            '$count',
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 48),
          const SizedBox(height: 12),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: onRetry,
            child: Text(StoreStrings.of(context).retry),
          ),
        ],
      ),
    ),
  );
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
