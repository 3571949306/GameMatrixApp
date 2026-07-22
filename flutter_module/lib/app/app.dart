import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import '../core/bridge/module_store_gateway.dart';
import '../features/module_store/state/store_controller.dart';
import 'router.dart';

class ModuleStoreApp extends StatefulWidget {
  const ModuleStoreApp({required this.gateway, super.key});
  final ModuleStoreGateway gateway;

  @override
  State<ModuleStoreApp> createState() => _ModuleStoreAppState();
}

class _ModuleStoreAppState extends State<ModuleStoreApp> {
  late final StoreController controller;

  @override
  void initState() {
    super.initState();
    controller = StoreController(widget.gateway);
    controller.initialize();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF5266D8);
    return StoreScope(
      controller: controller,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'GameMatrix Module Store',
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: seed),
          useMaterial3: true,
          cardTheme: const CardThemeData(clipBehavior: Clip.antiAlias),
        ),
        darkTheme: ThemeData(
          colorScheme: ColorScheme.fromSeed(
            seedColor: seed,
            brightness: Brightness.dark,
          ),
          useMaterial3: true,
          cardTheme: const CardThemeData(clipBehavior: Clip.antiAlias),
        ),
        themeMode: ThemeMode.system,
        supportedLocales: const [Locale('zh', 'CN'), Locale('en')],
        localizationsDelegates: const [
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        onGenerateRoute: StoreRouter.onGenerateRoute,
        initialRoute: '/store',
      ),
    );
  }
}
