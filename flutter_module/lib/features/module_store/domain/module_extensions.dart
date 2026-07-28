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

  /// 标准化 storeCategory 的中文显示名（6 类结果分类）。
  /// 空值或未知值回退为 "其他"。
  String get storeCategoryDisplayName =>
      storeCategoryDisplayNameOf(safeCategory);

  /// storeCategory 分组排序优先级（数字越小越靠前）。
  int get storeCategorySortOrder =>
      _storeCategorySortOrder[safeCategory] ?? 99;
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

/// 标准化 storeCategory wireValue → 中文显示名
const Map<String, String> _storeCategoryDisplayNames = {
  'entertainment_versus': '娱乐与对战',
  'learning_organization': '学习与整理',
  'reading_browsing': '阅读与浏览',
  'text_creation': '文本与创作',
  'device_network': '设备与网络',
  'personalization': '个性化',
};

/// 按 wireValue 查询 storeCategory 的中文显示名，未知值回退为 "其他"。
String storeCategoryDisplayNameOf(String? wireValue) {
  if (wireValue == null || wireValue.trim().isEmpty) return '其他';
  return _storeCategoryDisplayNames[wireValue] ?? '其他';
}

/// storeCategory 分组排序优先级
const Map<String, int> _storeCategorySortOrder = {
  'entertainment_versus': 0,
  'learning_organization': 1,
  'reading_browsing': 2,
  'text_creation': 3,
  'device_network': 4,
  'personalization': 5,
};

/// 按 storeCategory 分组并按结果优先级排序。
/// 返回有序的 (displayName, modules) 列表，跳过空分组。
/// "其他"（未知 storeCategory）排在最后。
List<MapEntry<String, List<NativeModule>>> groupModulesByStoreCategory(
  List<NativeModule> modules,
) {
  final Map<String, List<NativeModule>> bucketed = {};
  for (final module in modules) {
    final key = module.storeCategoryDisplayName;
    bucketed.putIfAbsent(key, () => []).add(module);
  }
  final entries = bucketed.entries.toList()
    ..sort((a, b) {
      final orderA = a.value.first.storeCategorySortOrder;
      final orderB = b.value.first.storeCategorySortOrder;
      return orderA.compareTo(orderB);
    });
  return entries.map((e) => MapEntry(e.key, e.value)).toList();
}
