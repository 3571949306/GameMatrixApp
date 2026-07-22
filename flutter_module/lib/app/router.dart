import 'package:flutter/material.dart';

import '../features/module_store/presentation/pages/downloads_page.dart';
import '../features/module_store/presentation/pages/managed_modules_page.dart';
import '../features/module_store/presentation/pages/module_detail_page.dart';
import '../features/module_store/presentation/pages/store_home_page.dart';

abstract final class StoreRouter {
  static Route<dynamic> onGenerateRoute(RouteSettings settings) {
    final name = settings.name ?? '/store';
    if (name == '/store') {
      return MaterialPageRoute(
        builder: (_) => const StoreHomePage(),
        settings: settings,
      );
    }
    if (name == '/store/downloads') {
      return MaterialPageRoute(
        builder: (_) => const DownloadsPage(),
        settings: settings,
      );
    }
    if (name == '/store/installed') {
      return MaterialPageRoute(
        builder: (_) => const ManagedModulesPage(mode: ManagedMode.installed),
        settings: settings,
      );
    }
    if (name == '/store/updates') {
      return MaterialPageRoute(
        builder: (_) => const ManagedModulesPage(mode: ManagedMode.updates),
        settings: settings,
      );
    }
    if (name.startsWith('/store/module/')) {
      return MaterialPageRoute(
        builder: (_) => ModuleDetailPage(
          moduleId: Uri.decodeComponent(
            name.substring('/store/module/'.length),
          ),
        ),
        settings: settings,
      );
    }
    return MaterialPageRoute(
      builder: (_) => const StoreHomePage(),
      settings: settings,
    );
  }
}
