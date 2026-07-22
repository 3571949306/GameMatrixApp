import 'dart:async';

import 'package:game_matrix_flutter_store/core/bridge/module_store_api.g.dart';
import 'package:game_matrix_flutter_store/core/bridge/module_store_gateway.dart';

class FakeModuleStoreGateway implements ModuleStoreGateway {
  FakeModuleStoreGateway({List<NativeModule>? modules})
    : modules = modules ?? [testModule()];

  final List<NativeModule> modules;
  final Map<String, String> preferences = {};
  final StreamController<NativeModuleEvent> eventController =
      StreamController<NativeModuleEvent>.broadcast();
  Duration preferenceReadDelay = Duration.zero;
  int activePreferenceReads = 0;
  int maxConcurrentPreferenceReads = 0;
  int getModuleCalls = 0;

  @override
  Stream<NativeModuleEvent> get events => eventController.stream;

  NativeCatalog _catalog() => NativeCatalog(
    schemaVersion: 2,
    catalogVersion: 1,
    source: 'test',
    offline: false,
    modules: modules,
  );

  NativeOperationResult _success(String moduleId) => NativeOperationResult(
    success: true,
    module: modules.firstWhere((module) => module.id == moduleId),
  );

  @override
  Future<NativeOperationResult> cancelDownload(String moduleId) async =>
      _success(moduleId);

  @override
  Future<NativeOperationResult> disable(String moduleId) async =>
      _success(moduleId);

  @override
  Future<NativeOperationResult> download(String moduleId) async =>
      _success(moduleId);

  @override
  Future<NativeOperationResult> enable(String moduleId) async =>
      _success(moduleId);

  @override
  Future<NativeCatalog> getCatalog() async => _catalog();

  @override
  Future<NativeModule> getModule(String moduleId) async {
    getModuleCalls++;
    return modules.firstWhere((module) => module.id == moduleId);
  }

  @override
  Future<String> getUiPreference(String key) async {
    activePreferenceReads++;
    if (activePreferenceReads > maxConcurrentPreferenceReads) {
      maxConcurrentPreferenceReads = activePreferenceReads;
    }
    try {
      if (preferenceReadDelay > Duration.zero) {
        await Future<void>.delayed(preferenceReadDelay);
      }
      return preferences[key] ?? '';
    } finally {
      activePreferenceReads--;
    }
  }

  @override
  Future<NativeOperationResult> open(String moduleId) async =>
      _success(moduleId);

  @override
  Future<void> openLegacyStore() async {}

  @override
  Future<NativeCatalog> refreshCatalog() async => _catalog();

  @override
  Future<NativeOperationResult> rollback(String moduleId) async =>
      _success(moduleId);

  @override
  Future<void> setUiPreference(String key, String value) async {
    preferences[key] = value;
  }

  @override
  Future<NativeOperationResult> uninstall(String moduleId) async =>
      _success(moduleId);

  @override
  Future<NativeOperationResult> update(String moduleId) async =>
      _success(moduleId);

  @override
  Future<List<NativeOperationResult?>> updateAll() async =>
      modules.map((module) => _success(module.id!)).toList();
}

NativeModule testModule({
  String id = 'test_web',
  String name = 'Test Web Module',
  String runtime = 'web',
  String state = 'not_installed',
  int fileSize = 1024,
  int versionCode = 1,
}) => NativeModule(
  id: id,
  name: name,
  shortDescription: 'A deterministic module used by Flutter tests.',
  description: 'Local test data only.',
  versionName: '1.0.0',
  versionCode: versionCode,
  runtime: runtime,
  deliveryType: 'remote',
  state: state,
  category: 'tools',
  fileSize: fileSize,
  featured: true,
  compatible: true,
  permissions: const [],
  dependencies: const [],
  tags: const ['test'],
);
