import 'package:flutter/material.dart';

import '../../../../app/store_strings.dart';
import '../../../../core/bridge/module_store_api.g.dart';
import '../../domain/module_detail_models.dart';
import '../../domain/module_extensions.dart';
import '../../state/store_controller.dart';

class ModuleDetailPage extends StatelessWidget {
  const ModuleDetailPage({required this.moduleId, super.key});

  final String moduleId;

  @override
  Widget build(BuildContext context) {
    final controller = StoreScope.read(context);
    return AnimatedBuilder(
      animation: controller,
      builder: (context, _) {
        final module = controller.catalog.modules
            .where((candidate) => candidate.safeId == moduleId)
            .firstOrNull;
        if (module == null) {
          return Scaffold(
            appBar: AppBar(),
            body: const Center(child: Text('Module not found')),
          );
        }
        return _DetailScaffold(module: module);
      },
    );
  }
}

class _DetailScaffold extends StatelessWidget {
  const _DetailScaffold({required this.module});
  final NativeModule module;

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final screenshots =
        module.screenshots?.whereType<String>().toList() ?? const [];
    return Scaffold(
      appBar: AppBar(title: Text(strings.details)),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(18, 8, 18, 120),
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 72,
                height: 72,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Text(
                  module.safeName.characters.take(2).toString().toUpperCase(),
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      module.safeName,
                      style: Theme.of(context).textTheme.headlineSmall,
                    ),
                    const SizedBox(height: 5),
                    Text(
                      '${module.safeRuntime} · ${module.safeDelivery} · v${module.versionName ?? '—'}',
                    ),
                    if (module.safeFileSize > 0)
                      Text(formatBytes(module.safeFileSize)),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          Text(
            module.description?.trim().isNotEmpty == true
                ? module.description!
                : module.summary,
          ),
          const SizedBox(height: 18),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              Chip(label: Text(module.safeState)),
              Chip(label: Text(module.storeCategoryDisplayName)),
              ...?module.tags?.whereType<String>().map(
                (tag) => Chip(label: Text(tag)),
              ),
            ],
          ),
          _DetailsSection(detail: ModuleDetail.fromJson(module.detailsJson)),
          _PrivacyCardSection(card: PrivacyCard.fromJson(module.privacyJson)),
          if (screenshots.isNotEmpty) ...[
            const SizedBox(height: 22),
            SizedBox(
              height: 180,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: screenshots.length,
                separatorBuilder: (_, _) => const SizedBox(width: 10),
                itemBuilder: (context, index) => ClipRRect(
                  borderRadius: BorderRadius.circular(16),
                  child: Image.network(
                    screenshots[index],
                    width: 300,
                    fit: BoxFit.cover,
                    errorBuilder: (_, _, _) => const SizedBox.shrink(),
                  ),
                ),
              ),
            ),
          ],
          _StringSection(
            title: strings.permissions,
            values:
                module.permissionsDescription?.whereType<String>().toList() ??
                module.permissions?.whereType<String>().toList() ??
                const [],
            emptyLabel: 'No additional permissions declared',
          ),
          _StringSection(
            title: strings.dependencies,
            values:
                module.dependencies?.whereType<String>().toList() ?? const [],
            emptyLabel: 'No module dependencies',
          ),
          _StringSection(
            title: strings.changelog,
            values: module.changelog?.whereType<String>().toList() ?? const [],
            emptyLabel: 'No changelog supplied',
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.all(14),
        child: _ActionBar(module: module),
      ),
    );
  }
}

/// 模块详情区块（#11.5）：展示价值描述、受众、离线能力、更新/卸载影响、亮点与限制。
class _DetailsSection extends StatelessWidget {
  const _DetailsSection({required this.detail});
  final ModuleDetail detail;

  @override
  Widget build(BuildContext context) {
    if (!detail.hasContent) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(Icons.info_outline, size: 20),
              const SizedBox(width: 6),
              Text('模块详情', style: theme.textTheme.titleMedium),
            ],
          ),
          const SizedBox(height: 10),
          if (detail.valueDescription.isNotEmpty)
            _DetailField(label: '价值描述', value: detail.valueDescription),
          if (detail.audience.isNotEmpty)
            _DetailField(label: '目标受众', value: detail.audience),
          if (detail.offlineCapability.isNotEmpty)
            _DetailField(label: '离线能力', value: detail.offlineCapability),
          if (detail.updateImpact.isNotEmpty)
            _DetailField(label: '更新影响', value: detail.updateImpact),
          if (detail.uninstallImpact.isNotEmpty)
            _DetailField(label: '卸载影响', value: detail.uninstallImpact),
          if (detail.highlights.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text('核心亮点', style: theme.textTheme.labelLarge),
            const SizedBox(height: 6),
            ...detail.highlights.map(
              (item) => Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.star, size: 16),
                    const SizedBox(width: 6),
                    Expanded(child: Text(item)),
                  ],
                ),
              ),
            ),
          ],
          if (detail.limitations.isNotEmpty) ...[
            const SizedBox(height: 12),
            Text('已知限制', style: theme.textTheme.labelLarge),
            const SizedBox(height: 6),
            ...detail.limitations.map(
              (item) => Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.warning_amber, size: 16),
                    const SizedBox(width: 6),
                    Expanded(child: Text(item)),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _DetailField extends StatelessWidget {
  const _DetailField({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 80,
            child: Text(
              label,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}

/// 隐私卡区块（#11.5）：展示本地/云端数据、网络域、同步位置、保存期限、删除方式。
class _PrivacyCardSection extends StatelessWidget {
  const _PrivacyCardSection({required this.card});
  final PrivacyCard card;

  @override
  Widget build(BuildContext context) {
    if (!card.hasContent) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 24),
      child: Card(
        color: theme.colorScheme.secondaryContainer,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(
                    Icons.privacy_tip_outlined,
                    size: 20,
                    color: theme.colorScheme.onSecondaryContainer,
                  ),
                  const SizedBox(width: 6),
                  Text(
                    '隐私卡',
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onSecondaryContainer,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              if (card.localData.isNotEmpty)
                _PrivacyField(label: '本地数据', value: card.localData),
              if (card.cloudData.isNotEmpty)
                _PrivacyField(label: '云端数据', value: card.cloudData),
              if (card.networkDomains.isNotEmpty)
                _PrivacyField(
                  label: '网络域',
                  value: card.networkDomains.join('、'),
                ),
              if (card.syncLocation.isNotEmpty)
                _PrivacyField(label: '同步位置', value: card.syncLocation),
              if (card.retentionPeriod.isNotEmpty)
                _PrivacyField(label: '保存期限', value: card.retentionPeriod),
              if (card.deletionMethod.isNotEmpty)
                _PrivacyField(label: '删除方式', value: card.deletionMethod),
            ],
          ),
        ),
      ),
    );
  }
}

class _PrivacyField extends StatelessWidget {
  const _PrivacyField({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 80,
            child: Text(
              label,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSecondaryContainer.withValues(
                  alpha: 0.7,
                ),
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSecondaryContainer,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StringSection extends StatelessWidget {
  const _StringSection({
    required this.title,
    required this.values,
    required this.emptyLabel,
  });
  final String title;
  final List<String> values;
  final String emptyLabel;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(height: 8),
        if (values.isEmpty)
          Text(emptyLabel, style: Theme.of(context).textTheme.bodySmall)
        else
          ...values.map(
            (value) => Padding(
              padding: const EdgeInsets.only(bottom: 5),
              child: Text('• $value'),
            ),
          ),
      ],
    ),
  );
}

class _ActionBar extends StatelessWidget {
  const _ActionBar({required this.module});
  final NativeModule module;

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final primary = switch (module.safeState) {
      'not_installed' => (strings.install, 'download'),
      'update_available' => (strings.update, 'update'),
      'disabled' => (strings.enable, 'enable'),
      'queued' ||
      'downloading' ||
      'verifying' ||
      'installing' => (strings.cancel, 'cancel'),
      _ => (strings.open, 'open'),
    };
    return Row(
      children: [
        if (module.isInstalled) ...[
          PopupMenuButton<String>(
            tooltip: 'Manage module',
            onSelected: (action) => _perform(context, action),
            itemBuilder: (_) => [
              PopupMenuItem(
                value: module.safeState == 'disabled' ? 'enable' : 'disable',
                child: Text(
                  module.safeState == 'disabled'
                      ? strings.enable
                      : strings.disable,
                ),
              ),
              if (module.rollbackAvailable == true)
                PopupMenuItem(value: 'rollback', child: Text(strings.rollback)),
              if (module.required != true)
                PopupMenuItem(
                  value: 'uninstall',
                  child: Text(strings.uninstall),
                ),
            ],
          ),
          const SizedBox(width: 10),
        ],
        Expanded(
          child: FilledButton.icon(
            onPressed: module.compatible == false
                ? null
                : () => _perform(context, primary.$2),
            icon: Icon(
              primary.$2 == 'open' ? Icons.open_in_new : Icons.download,
            ),
            label: Text(primary.$1),
          ),
        ),
      ],
    );
  }

  Future<void> _perform(BuildContext context, String action) async {
    final strings = StoreStrings.of(context);
    if (action == 'uninstall') {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(strings.uninstall),
          content: Text(strings.confirmUninstall),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(strings.cancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(strings.confirm),
            ),
          ],
        ),
      );
      if (confirmed != true || !context.mounted) return;
    }
    final controller = StoreScope.read(context);
    final result = action == 'cancel'
        ? await controller.gateway.cancelDownload(module.safeId)
        : await controller.perform(action, module);
    if (context.mounted && result.success != true) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(result.error?.message ?? strings.operationFailed),
        ),
      );
    }
  }
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
