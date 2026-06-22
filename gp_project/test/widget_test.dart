import 'package:flutter_test/flutter_test.dart';

import 'package:gp_project/main.dart';

void main() {
  testWidgets('App launches smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const VirtualOfficeApp());
    await tester.pump();
    // App launches without throwing
    expect(find.byType(VirtualOfficeApp), findsOneWidget);
  });
}
