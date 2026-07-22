import 'package:flutter/material.dart';

import '../../../../app/store_strings.dart';
import '../../state/store_controller.dart';
import '../widgets/module_card.dart';

enum ManagedMode { installed, updates }

class ManagedModulesPage extends StatelessWidget {
  const ManagedModulesPage({required this.mode, super.key});
  final ManagedMode mode;

  @override
  Widget build(BuildContext context) {
    final strings = StoreStrings.of(context);
    final controller = StoreScope.read(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(
          mode == ManagedMode.installed ? strings.installed : strings.updates,
        ),
        actions: [
          if (mode == ManagedMode.updates)
            TextButton.icon(
              onPressed:
                  controller.updates.modules.isEmpty ||
                      controller.updates.updatingAll
                  ? null
                  : controller.updateAll,
              icon: controller.updates.updatingAll
                  ? const SizedBox.square(
                      dimension: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.system_update_alt),
              label: Text(strings.updateAll),
            ),
        ],
      ),
      body: AnimatedBuilder(
        animation: controller,
        builder: (context, _) {
          final modules = mode == ManagedMode.installed
              ? controller.installed.modules
              : controller.updates.modules;
          if (modules.isEmpty) return Center(child: Text(strings.empty));
          return ListView.builder(
            padding: const EdgeInsets.fromLTRB(10, 8, 10, 24),
            itemCount: modules.length,
            itemBuilder: (_, index) => ModuleCard(module: modules[index]),
          );
        },
      ),
    );
  }
}
