import 'package:amn/main.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('home shows the empty state', (tester) async {
    await tester.pumpWidget(const AmnApp());
    expect(find.text('No tools yet'), findsOneWidget);
  });
}
