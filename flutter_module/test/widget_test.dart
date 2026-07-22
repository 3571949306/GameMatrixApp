import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:game_matrix_flutter_store/app/app.dart';

import 'fake_gateway.dart';

void main() {
  testWidgets('store renders native catalog and opens details', (tester) async {
    tester.binding.platformDispatcher.localeTestValue = const Locale('en');
    addTearDown(tester.binding.platformDispatcher.clearLocaleTestValue);

    await tester.pumpWidget(ModuleStoreApp(gateway: FakeModuleStoreGateway()));
    await tester.pumpAndSettle();

    expect(find.text('Module Store'), findsOneWidget);
    expect(find.text('Test Web Module'), findsOneWidget);

    await tester.tap(find.text('Test Web Module'));
    await tester.pumpAndSettle();

    expect(find.text('Module details'), findsOneWidget);
    expect(find.textContaining('web'), findsWidgets);
  });
}
