import 'package:flutter/material.dart';

import '../../../../app/store_strings.dart';
import '../../../../core/bridge/module_store_api.g.dart';
import '../../domain/module_extensions.dart';
import '../../state/store_controller.dart';

class ModuleCard extends StatelessWidget {
  const ModuleCard({required this.module, this.compact = false, super.key});
  final NativeModule module;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final controller = StoreScope.read(context);
    return AnimatedBuilder(
      animation: controller.downloads,
      builder: (context, _) {
        final event = controller.downloads.eventsByModule[module.safeId];
        final progress = event?.progress;
        final card = Card(
          child: InkWell(
            onTap: () => Navigator.pushNamed(
              context,
              '/store/module/${Uri.encodeComponent(module.safeId)}',
            ),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _ModuleIcon(module: module),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              module.safeName,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                            const SizedBox(height: 3),
                            Text(
                              module.summary,
                              maxLines: compact ? 2 : 3,
                              overflow: TextOverflow.ellipsis,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Wrap(
                    spacing: 6,
                    runSpacing: 5,
                    children: [
                      _Tag(module.safeRuntime),
                      _Tag(module.safeDelivery),
                      _Tag('v${module.versionName ?? '1.0.0'}'),
                      if (module.builtIn == true) const _Tag('Built-in'),
                      if (module.safeFileSize > 0)
                        _Tag(formatBytes(module.safeFileSize)),
                    ],
                  ),
                  if (progress != null && module.isBusy) ...[
                    const SizedBox(height: 10),
                    LinearProgressIndicator(
                      value: (progress.percent ?? 0) > 0
                          ? (progress.percent ?? 0) / 100
                          : null,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '${progress.percent ?? 0}% · ${formatBytes(progress.downloadedBytes ?? 0)} / ${formatBytes(progress.totalBytes ?? 0)}',
                      style: Theme.of(context).textTheme.labelSmall,
                    ),
                  ],
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          _stateLabel(module.safeState, strings),
                          style: Theme.of(context).textTheme.labelMedium,
                        ),
                      ),
                      _PrimaryAction(module: module),
                    ],
                  ),
                ],
              ),
            ),
          ),
        );
        return Semantics(
          label: '${module.safeName}, ${module.safeState}',
          child: card,
        );
      },
    );
  }
}

class _ModuleIcon extends StatelessWidget {
  const _ModuleIcon({required this.module});
  final NativeModule module;
  @override
  Widget build(BuildContext context) => Container(
    width: 52,
    height: 52,
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.primaryContainer,
      borderRadius: BorderRadius.circular(15),
    ),
    alignment: Alignment.center,
    child: Text(
      module.safeName.characters.take(2).toString().toUpperCase(),
      style: Theme.of(context).textTheme.titleMedium?.copyWith(
        color: Theme.of(context).colorScheme.onPrimaryContainer,
        fontWeight: FontWeight.w700,
      ),
    ),
  );
}

class _Tag extends StatelessWidget {
  const _Tag(this.label);
  final String label;
  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: Theme.of(context).colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(999),
    ),
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Text(label, style: Theme.of(context).textTheme.labelSmall),
    ),
  );
}

class _PrimaryAction extends StatelessWidget {
  const _PrimaryAction({required this.module});
  final NativeModule module;

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final (label, action) = switch (module.safeState) {
      'not_installed' => (strings.install, 'download'),
      'update_available' => (strings.update, 'update'),
      'disabled' => (strings.enable, 'enable'),
      'queued' ||
      'downloading' ||
      'verifying' ||
      'installing' => (strings.cancel, 'cancel'),
      _ => (strings.open, 'open'),
    };
    return FilledButton.tonal(
      onPressed: () async {
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
      },
      child: Text(label),
    );
  }
}

String _stateLabel(String state, StoreStrings strings) => switch (state) {
  'installed' => strings.installed,
  'update_available' => strings.updates,
  'disabled' => strings.disable,
  'queued' => 'Queued',
  'downloading' => 'Downloading',
  'verifying' => 'Verifying',
  'installing' => 'Installing',
  'failed' => strings.operationFailed,
  _ => 'Not installed',
};
