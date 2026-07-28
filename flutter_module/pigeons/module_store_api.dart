import 'package:pigeon/pigeon.dart';

/// Pigeon contract shared by the Flutter store and the authoritative Kotlin
/// module core. All business state comes from the host; Flutter only owns UI
/// preferences such as filters and search history.
class NativeModule {
  String? id;
  String? name;
  String? shortDescription;
  String? description;
  String? versionName;
  int? versionCode;
  int? installedVersionCode;
  String? runtime;
  String? deliveryType;
  String? state;
  String? route;
  String? entryClass;
  String? entry;
  String? serviceType;
  String? launcherId;
  String? iconUrl;
  String? category;
  int? fileSize;
  bool? builtIn;
  bool? required;
  bool? featured;
  bool? enabled;
  bool? updateAvailable;
  bool? compatible;
  bool? rollbackAvailable;
  int? minHostVersionCode;
  int? maxHostVersionCode;
  List<String?>? permissions;
  List<String?>? permissionsDescription;
  List<String?>? dependencies;
  List<String?>? tags;
  List<String?>? screenshots;
  List<String?>? changelog;
  /// 模块详情 JSON 字符串（#11.1）：valueDescription/audience/offlineCapability 等
  String? detailsJson;
  /// 隐私卡 JSON 字符串（#11.2）：localData/cloudData/networkDomains 等
  String? privacyJson;
}

class NativeCatalog {
  int? schemaVersion;
  int? catalogVersion;
  String? generatedAt;
  String? source;
  bool? offline;
  List<NativeModule?>? modules;
}

class NativeModuleError {
  String? errorCode;
  String? message;
  String? moduleId;
  String? runtime;
  bool? recoverable;
  String? suggestedAction;
  String? technicalDetails;
}

class NativeOperationResult {
  bool? success;
  NativeModule? module;
  NativeModuleError? error;
}

class NativeDownloadProgress {
  String? moduleId;
  int? downloadedBytes;
  int? totalBytes;
  int? speedKbps;
  int? percent;
  String? state;
}

class NativeModuleEvent {
  String? eventType;
  String? moduleId;
  String? runtime;
  String? state;
  int? timestampMillis;
  NativeDownloadProgress? progress;
  NativeModuleError? error;
}

@HostApi()
abstract class ModuleStoreHostApi {
  @async
  NativeCatalog getCatalog();

  @async
  NativeCatalog refreshCatalog();

  @async
  List<NativeModule?> getInstalledModules();

  @async
  NativeModule getModuleStatus(String moduleId);

  @async
  NativeModule getModuleDetails(String moduleId);

  @async
  NativeOperationResult downloadModule(String moduleId);

  NativeOperationResult cancelDownload(String moduleId);

  @async
  NativeOperationResult installModule(String moduleId);

  @async
  NativeOperationResult updateModule(String moduleId);

  @async
  NativeOperationResult uninstallModule(String moduleId);

  @async
  NativeOperationResult enableModule(String moduleId);

  @async
  NativeOperationResult disableModule(String moduleId);

  @async
  NativeOperationResult rollbackModule(String moduleId);

  @async
  NativeOperationResult openModule(String moduleId);

  NativeDownloadProgress getDownloadProgress(String moduleId);

  @async
  List<NativeModule?> getUpdateableModules();

  @async
  List<NativeOperationResult?> updateAllModules();

  String getUiPreference(String key);

  void setUiPreference(String key, String value);

  void openLegacyStore();
}

@FlutterApi()
abstract class ModuleStoreFlutterApi {
  void onModuleEvent(NativeModuleEvent event);
}
