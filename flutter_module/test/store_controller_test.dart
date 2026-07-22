import 'package:flutter_test/flutter_test.dart';
import 'package:game_matrix_flutter_store/core/bridge/module_store_api.g.dart';
import 'package:game_matrix_flutter_store/features/module_store/state/store_controller.dart';

import 'fake_gateway.dart';

void main() {
  test('filters combine query, runtime, state, size, and sort', () {
    final gateway = FakeModuleStoreGateway(
      modules: [
        testModule(id: 'small_web', name: 'Alpha Web', fileSize: 1024),
        testModule(
          id: 'large_native',
          name: 'Beta Native',
          runtime: 'native_service',
          state: 'installed',
          fileSize: 80 * 1024 * 1024,
          versionCode: 2,
        ),
      ],
    );
    final filters = StoreFilterState(gateway)
      ..query = 'beta'
      ..runtimeFilter = 'native_service'
      ..installFilter = InstallFilter.installed
      ..sizeFilter = SizeFilter.large
      ..sort = StoreSort.version;

    final result = filters.apply(gateway.modules);

    expect(result.map((module) => module.id), ['large_native']);
    filters.dispose();
  });

  test('corrupt UI preferences recover to defaults', () async {
    final gateway = FakeModuleStoreGateway();
    gateway.preferences['search_history'] = '{not-json';
    gateway.preferences['filter_state'] = '{not-json';
    final filters = StoreFilterState(gateway);

    await filters.loadPreferences();

    expect(filters.history, isEmpty);
    expect(filters.runtimeFilter, 'all');
    expect(filters.sort, StoreSort.featured);
    filters.dispose();
  });

  test('UI preferences are loaded concurrently', () async {
    final gateway = FakeModuleStoreGateway()
      ..preferenceReadDelay = const Duration(milliseconds: 20);
    final filters = StoreFilterState(gateway);

    await filters.loadPreferences();

    expect(gateway.maxConcurrentPreferenceReads, 4);
    filters.dispose();
  });

  test('visible modules are cached between state changes', () async {
    final gateway = FakeModuleStoreGateway();
    final controller = StoreController(gateway);

    await controller.initialize();

    expect(
      identical(controller.visibleModules, controller.visibleModules),
      isTrue,
    );
    controller.dispose();
  });

  test('download progress does not query the full native module', () async {
    final gateway = FakeModuleStoreGateway();
    final controller = StoreController(gateway);
    await controller.initialize();

    gateway.eventController.add(
      NativeModuleEvent(
        eventType: 'DownloadProgress',
        moduleId: 'test_web',
        state: 'downloading',
      ),
    );
    await pumpEventQueue();
    expect(gateway.getModuleCalls, 0);

    gateway.eventController.add(
      NativeModuleEvent(
        eventType: 'InstallStarted',
        moduleId: 'test_web',
        state: 'installing',
      ),
    );
    await pumpEventQueue();
    expect(gateway.getModuleCalls, 1);
    controller.dispose();
  });
}
