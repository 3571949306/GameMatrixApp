import 'dart:async';

import 'module_store_api.g.dart';

abstract interface class ModuleStoreGateway {
  Stream<NativeModuleEvent> get events;
  Future<NativeCatalog> getCatalog();
  Future<NativeCatalog> refreshCatalog();
  Future<NativeModule> getModule(String moduleId);
  Future<NativeOperationResult> download(String moduleId);
  Future<NativeOperationResult> cancelDownload(String moduleId);
  Future<NativeOperationResult> update(String moduleId);
  Future<NativeOperationResult> uninstall(String moduleId);
  Future<NativeOperationResult> enable(String moduleId);
  Future<NativeOperationResult> disable(String moduleId);
  Future<NativeOperationResult> rollback(String moduleId);
  Future<NativeOperationResult> open(String moduleId);
  Future<List<NativeOperationResult?>> updateAll();
  Future<String> getUiPreference(String key);
  Future<void> setUiPreference(String key, String value);
  Future<void> openLegacyStore();
}

class PigeonModuleStoreGateway
    implements ModuleStoreGateway, ModuleStoreFlutterApi {
  PigeonModuleStoreGateway({ModuleStoreHostApi? hostApi})
    : _hostApi = hostApi ?? ModuleStoreHostApi() {
    ModuleStoreFlutterApi.setUp(this);
  }

  final ModuleStoreHostApi _hostApi;
  final StreamController<NativeModuleEvent> _events =
      StreamController<NativeModuleEvent>.broadcast(sync: true);

  @override
  Stream<NativeModuleEvent> get events => _events.stream;

  @override
  void onModuleEvent(NativeModuleEvent event) => _events.add(event);

  @override
  Future<NativeCatalog> getCatalog() => _hostApi.getCatalog();

  @override
  Future<NativeCatalog> refreshCatalog() => _hostApi.refreshCatalog();

  @override
  Future<NativeModule> getModule(String moduleId) =>
      _hostApi.getModuleStatus(moduleId);

  @override
  Future<NativeOperationResult> download(String moduleId) =>
      _hostApi.downloadModule(moduleId);

  @override
  Future<NativeOperationResult> cancelDownload(String moduleId) =>
      _hostApi.cancelDownload(moduleId);

  @override
  Future<NativeOperationResult> update(String moduleId) =>
      _hostApi.updateModule(moduleId);

  @override
  Future<NativeOperationResult> uninstall(String moduleId) =>
      _hostApi.uninstallModule(moduleId);

  @override
  Future<NativeOperationResult> enable(String moduleId) =>
      _hostApi.enableModule(moduleId);

  @override
  Future<NativeOperationResult> disable(String moduleId) =>
      _hostApi.disableModule(moduleId);

  @override
  Future<NativeOperationResult> rollback(String moduleId) =>
      _hostApi.rollbackModule(moduleId);

  @override
  Future<NativeOperationResult> open(String moduleId) =>
      _hostApi.openModule(moduleId);

  @override
  Future<List<NativeOperationResult?>> updateAll() =>
      _hostApi.updateAllModules();

  @override
  Future<String> getUiPreference(String key) => _hostApi.getUiPreference(key);

  @override
  Future<void> setUiPreference(String key, String value) =>
      _hostApi.setUiPreference(key, value);

  @override
  Future<void> openLegacyStore() => _hostApi.openLegacyStore();
}
