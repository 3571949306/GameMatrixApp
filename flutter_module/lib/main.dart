import 'package:flutter/widgets.dart';

import 'app/app.dart';
import 'core/bridge/module_store_gateway.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(ModuleStoreApp(gateway: PigeonModuleStoreGateway()));
}
