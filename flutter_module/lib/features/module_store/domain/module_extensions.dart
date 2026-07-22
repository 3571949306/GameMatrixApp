import '../../../core/bridge/module_store_api.g.dart';

extension NativeModuleView on NativeModule {
  String get safeId => id ?? '';
  String get safeName => name?.trim().isNotEmpty == true ? name! : safeId;
  String get summary => shortDescription?.trim().isNotEmpty == true
      ? shortDescription!
      : (description ?? '');
  String get safeState => state ?? 'not_installed';
  String get safeRuntime => runtime ?? 'android';
  String get safeDelivery => deliveryType ?? 'builtin';
  String get safeCategory =>
      category?.trim().isNotEmpty == true ? category! : 'other';
  bool get isInstalled => const {
    'installed',
    'update_available',
    'disabled',
    'rolled_back',
  }.contains(safeState);
  bool get isBusy => const {
    'queued',
    'downloading',
    'verifying',
    'installing',
    'rolling_back',
    'uninstalling',
  }.contains(safeState);
  int get safeFileSize => fileSize ?? 0;
  int get safeVersionCode => versionCode ?? 0;
}

String formatBytes(int bytes) {
  if (bytes <= 0) return '—';
  if (bytes < 1024) return '$bytes B';
  final kb = bytes / 1024;
  if (kb < 1024) return '${kb.toStringAsFixed(1)} KB';
  final mb = kb / 1024;
  if (mb < 1024) return '${mb.toStringAsFixed(1)} MB';
  return '${(mb / 1024).toStringAsFixed(1)} GB';
}
