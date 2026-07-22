import 'package:flutter/material.dart';

import '../../../../app/store_strings.dart';
import '../../domain/module_extensions.dart';
import '../../state/store_controller.dart';

class DownloadsPage extends StatelessWidget {
  const DownloadsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final controller = StoreScope.read(context);
    return Scaffold(
      appBar: AppBar(title: Text(strings.downloads)),
      body: AnimatedBuilder(
        animation: controller.downloads,
        builder: (context, _) {
          final events = controller.downloads.eventsByModule.values.toList()
            ..sort(
              (a, b) =>
                  (b.timestampMillis ?? 0).compareTo(a.timestampMillis ?? 0),
            );
          if (events.isEmpty) return Center(child: Text(strings.noTasks));
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: events.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final event = events[index];
              final progress = event.progress;
              final moduleId = event.moduleId ?? '';
              final active = const {
                'queued',
                'downloading',
                'verifying',
                'installing',
              }.contains(event.state);
              return Card(
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: Text(
                              moduleId,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ),
                          if (active)
                            IconButton(
                              tooltip: strings.cancel,
                              onPressed: () =>
                                  controller.gateway.cancelDownload(moduleId),
                              icon: const Icon(Icons.close),
                            ),
                        ],
                      ),
                      Text(event.state ?? event.eventType ?? 'unknown'),
                      if (progress != null) ...[
                        const SizedBox(height: 10),
                        LinearProgressIndicator(
                          value: (progress.percent ?? 0) > 0
                              ? (progress.percent ?? 0) / 100
                              : null,
                        ),
                        const SizedBox(height: 5),
                        Text(
                          '${progress.percent ?? 0}% · ${formatBytes(progress.downloadedBytes ?? 0)} / ${formatBytes(progress.totalBytes ?? 0)} · ${progress.speedKbps ?? 0} KB/s',
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                      ],
                      if (event.error?.message != null) ...[
                        const SizedBox(height: 7),
                        Text(
                          event.error!.message!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
