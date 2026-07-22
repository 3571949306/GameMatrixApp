import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';

import '../../../core/bridge/module_store_api.g.dart';
import '../../../core/bridge/module_store_gateway.dart';
import '../domain/module_extensions.dart';

class CatalogState extends ChangeNotifier {
  CatalogState(this.gateway);
  final ModuleStoreGateway gateway;
  NativeCatalog? catalog;
  bool loading = false;
  String? error;
  List<NativeModule> _modules = const [];

  List<NativeModule> get modules => _modules;

  Future<void> load({bool refresh = false}) async {
    loading = true;
    error = null;
    notifyListeners();
    try {
      final loaded = refresh
          ? await gateway.refreshCatalog()
          : await gateway.getCatalog();
      catalog = loaded;
      _modules =
          loaded.modules?.whereType<NativeModule>().toList(growable: false) ??
          const [];
    } catch (exception) {
      error = exception.toString();
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  void replaceModule(NativeModule updated) {
    final value = catalog;
    if (value == null) return;
    final next = [...?value.modules];
    final index = next.indexWhere((module) => module?.id == updated.id);
    if (index >= 0) next[index] = updated;
    catalog = NativeCatalog(
      schemaVersion: value.schemaVersion,
      catalogVersion: value.catalogVersion,
      generatedAt: value.generatedAt,
      source: value.source,
      offline: value.offline,
      modules: next,
    );
    _modules = next.whereType<NativeModule>().toList(growable: false);
    notifyListeners();
  }
}

enum InstallFilter { all, installed, updateAvailable, disabled, notInstalled }

enum SizeFilter { all, small, medium, large }

enum VersionFilter { all, v1OrAbove, v2OrAbove }

enum StoreSort { featured, name, version, size }

class StoreFilterState extends ChangeNotifier {
  StoreFilterState(this.gateway);
  final ModuleStoreGateway gateway;
  String query = '';
  String category = 'all';
  String runtimeFilter = 'all';
  InstallFilter installFilter = InstallFilter.all;
  SizeFilter sizeFilter = SizeFilter.all;
  VersionFilter versionFilter = VersionFilter.all;
  StoreSort sort = StoreSort.featured;
  bool grid = false;
  List<String> history = const [];
  Timer? _debounce;

  Future<void> loadPreferences() async {
    try {
      final values = await Future.wait([
        _readPreference('search_history'),
        _readPreference('filter_state'),
        _readPreference('sort_mode'),
        _readPreference('view_mode'),
      ]);
      final encoded = values[0];
      if (encoded.isNotEmpty) {
        history = (jsonDecode(encoded) as List<dynamic>)
            .whereType<String>()
            .take(8)
            .toList(growable: false);
      }
      final filterJson = values[1];
      if (filterJson.isNotEmpty) {
        final values = jsonDecode(filterJson) as Map<String, dynamic>;
        runtimeFilter = values['runtimeType'] as String? ?? 'all';
        installFilter = InstallFilter.values.byName(
          values['installFilter'] as String? ?? InstallFilter.all.name,
        );
        sizeFilter = SizeFilter.values.byName(
          values['sizeFilter'] as String? ?? SizeFilter.all.name,
        );
        versionFilter = VersionFilter.values.byName(
          values['versionFilter'] as String? ?? VersionFilter.all.name,
        );
      }
      final savedSort = values[2];
      if (savedSort.isNotEmpty) sort = StoreSort.values.byName(savedSort);
      grid = values[3] == 'grid';
    } on Object {
      // Corrupt UI-only preferences must never block the authoritative catalog.
      history = const [];
      runtimeFilter = 'all';
      installFilter = InstallFilter.all;
      sizeFilter = SizeFilter.all;
      versionFilter = VersionFilter.all;
      sort = StoreSort.featured;
      grid = false;
    }
    notifyListeners();
  }

  Future<String> _readPreference(String key) async {
    try {
      return await gateway.getUiPreference(key);
    } on Object {
      return '';
    }
  }

  void setQueryDebounced(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      query = value.trim();
      notifyListeners();
    });
  }

  Future<void> commitSearch(String value) async {
    query = value.trim();
    if (query.isNotEmpty) {
      history = [
        query,
        ...history.where((item) => item != query),
      ].take(8).toList();
      await gateway.setUiPreference('search_history', jsonEncode(history));
    }
    notifyListeners();
  }

  Future<void> clearHistory() async {
    history = const [];
    await gateway.setUiPreference('search_history', '[]');
    notifyListeners();
  }

  Future<void> toggleGrid() async {
    grid = !grid;
    await gateway.setUiPreference('view_mode', grid ? 'grid' : 'list');
    notifyListeners();
  }

  void setCategory(String value) {
    category = value;
    notifyListeners();
  }

  Future<void> setRuntimeType(String value) async {
    runtimeFilter = value;
    await _persistAdvanced();
  }

  Future<void> setInstallFilter(InstallFilter value) async {
    installFilter = value;
    await _persistAdvanced();
  }

  Future<void> setSizeFilter(SizeFilter value) async {
    sizeFilter = value;
    await _persistAdvanced();
  }

  Future<void> setVersionFilter(VersionFilter value) async {
    versionFilter = value;
    await _persistAdvanced();
  }

  Future<void> setSort(StoreSort value) async {
    sort = value;
    await gateway.setUiPreference('sort_mode', sort.name);
    notifyListeners();
  }

  Future<void> _persistAdvanced() async {
    await gateway.setUiPreference(
      'filter_state',
      jsonEncode({
        'runtimeType': runtimeFilter,
        'installFilter': installFilter.name,
        'sizeFilter': sizeFilter.name,
        'versionFilter': versionFilter.name,
      }),
    );
    notifyListeners();
  }

  Future<void> resetAdvanced() async {
    runtimeFilter = 'all';
    installFilter = InstallFilter.all;
    sizeFilter = SizeFilter.all;
    versionFilter = VersionFilter.all;
    sort = StoreSort.featured;
    await Future.wait([
      gateway.setUiPreference('filter_state', '{}'),
      gateway.setUiPreference('sort_mode', StoreSort.featured.name),
    ]);
    notifyListeners();
  }

  List<NativeModule> apply(List<NativeModule> modules) {
    final normalizedQuery = query.toLowerCase();
    final filtered = modules.where((module) {
      if (category != 'all' && module.safeCategory != category) return false;
      if (runtimeFilter != 'all' && module.safeRuntime != runtimeFilter) {
        return false;
      }
      if (normalizedQuery.isNotEmpty) {
        final haystack = [
          module.safeName,
          module.summary,
          ...?module.tags,
        ].join(' ').toLowerCase();
        if (!haystack.contains(normalizedQuery)) return false;
      }
      final installMatches = switch (installFilter) {
        InstallFilter.installed => module.isInstalled,
        InstallFilter.updateAvailable => module.safeState == 'update_available',
        InstallFilter.disabled => module.safeState == 'disabled',
        InstallFilter.notInstalled => module.safeState == 'not_installed',
        InstallFilter.all => true,
      };
      if (!installMatches) return false;
      final sizeMatches = switch (sizeFilter) {
        SizeFilter.small => module.safeFileSize <= 10 * 1024 * 1024,
        SizeFilter.medium =>
          module.safeFileSize > 10 * 1024 * 1024 &&
              module.safeFileSize <= 50 * 1024 * 1024,
        SizeFilter.large => module.safeFileSize > 50 * 1024 * 1024,
        SizeFilter.all => true,
      };
      if (!sizeMatches) return false;
      final major =
          int.tryParse((module.versionName ?? '0').split('.').first) ?? 0;
      if (versionFilter == VersionFilter.v1OrAbove && major < 1) return false;
      if (versionFilter == VersionFilter.v2OrAbove && major < 2) return false;
      return true;
    }).toList();
    switch (sort) {
      case StoreSort.featured:
        filtered.sort(
          (a, b) => (b.featured == true ? 1 : 0).compareTo(
            a.featured == true ? 1 : 0,
          ),
        );
        break;
      case StoreSort.name:
        filtered.sort((a, b) => a.safeName.compareTo(b.safeName));
        break;
      case StoreSort.version:
        filtered.sort((a, b) => b.safeVersionCode.compareTo(a.safeVersionCode));
        break;
      case StoreSort.size:
        filtered.sort((a, b) => a.safeFileSize.compareTo(b.safeFileSize));
        break;
    }
    return filtered;
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }
}

class DownloadQueueState extends ChangeNotifier {
  final Map<String, NativeModuleEvent> eventsByModule = {};

  bool accept(NativeModuleEvent event) {
    final id = event.moduleId;
    if (id == null || id.isEmpty) return false;
    final activeBefore = active.length;
    eventsByModule[id] = event;
    final activeChanged = active.length != activeBefore;
    notifyListeners();
    return activeChanged;
  }

  Iterable<NativeModuleEvent> get active => eventsByModule.values.where(
    (event) => const {
      'queued',
      'downloading',
      'verifying',
      'installing',
      'failed',
    }.contains(event.state),
  );
}

class InstalledModulesState extends ChangeNotifier {
  List<NativeModule> modules = const [];
  void update(List<NativeModule> source) {
    modules = source
        .where((module) => module.isInstalled)
        .toList(growable: false);
    notifyListeners();
  }
}

class UpdateState extends ChangeNotifier {
  List<NativeModule> modules = const [];
  bool updatingAll = false;
  void update(List<NativeModule> source) {
    modules = source
        .where((module) => module.safeState == 'update_available')
        .toList(growable: false);
    notifyListeners();
  }

  void setUpdatingAll(bool value) {
    updatingAll = value;
    notifyListeners();
  }
}

class StoreController extends ChangeNotifier {
  StoreController(this.gateway)
    : catalog = CatalogState(gateway),
      filters = StoreFilterState(gateway) {
    catalog.addListener(_onCatalogChanged);
    filters.addListener(_onFiltersChanged);
    _eventSubscription = gateway.events.listen(_onEvent);
  }

  final ModuleStoreGateway gateway;
  final CatalogState catalog;
  final StoreFilterState filters;
  final DownloadQueueState downloads = DownloadQueueState();
  final InstalledModulesState installed = InstalledModulesState();
  final UpdateState updates = UpdateState();
  late final StreamSubscription<NativeModuleEvent> _eventSubscription;
  List<NativeModule> _visibleModules = const [];

  Future<void> initialize() async {
    await Future.wait([filters.loadPreferences(), catalog.load()]);
  }

  List<NativeModule> get visibleModules => _visibleModules;

  Future<void> refresh() => catalog.load(refresh: true);

  Future<NativeOperationResult> perform(
    String action,
    NativeModule module,
  ) async {
    final id = module.safeId;
    final result = switch (action) {
      'download' => await gateway.download(id),
      'update' => await gateway.update(id),
      'uninstall' => await gateway.uninstall(id),
      'enable' => await gateway.enable(id),
      'disable' => await gateway.disable(id),
      'rollback' => await gateway.rollback(id),
      'open' => await gateway.open(id),
      _ => throw ArgumentError.value(action, 'action'),
    };
    final updated = result.module;
    if (updated != null) catalog.replaceModule(updated);
    return result;
  }

  Future<void> updateAll() async {
    updates.setUpdatingAll(true);
    notifyListeners();
    try {
      await gateway.updateAll();
    } finally {
      updates.setUpdatingAll(false);
      notifyListeners();
    }
  }

  void _onCatalogChanged() {
    installed.update(catalog.modules);
    updates.update(catalog.modules);
    _visibleModules = filters.apply(catalog.modules);
    notifyListeners();
  }

  void _onFiltersChanged() {
    _visibleModules = filters.apply(catalog.modules);
    notifyListeners();
  }

  Future<void> _onEvent(NativeModuleEvent event) async {
    if (downloads.accept(event)) notifyListeners();
    final id = event.moduleId;
    if (id == null || id.isEmpty) return;
    if (event.eventType == 'DownloadProgress') return;
    try {
      catalog.replaceModule(await gateway.getModule(id));
    } catch (_) {
      // A later catalog refresh remains the authoritative recovery path.
    }
  }

  @override
  void dispose() {
    _eventSubscription.cancel();
    catalog.removeListener(_onCatalogChanged);
    filters.removeListener(_onFiltersChanged);
    catalog.dispose();
    filters.dispose();
    downloads.dispose();
    installed.dispose();
    updates.dispose();
    super.dispose();
  }
}

class StoreScope extends InheritedNotifier<StoreController> {
  const StoreScope({
    required StoreController controller,
    required super.child,
    super.key,
  }) : super(notifier: controller);

  static StoreController of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<StoreScope>()!.notifier!;

  static StoreController read(BuildContext context) =>
      context.getInheritedWidgetOfExactType<StoreScope>()!.notifier!;
}
