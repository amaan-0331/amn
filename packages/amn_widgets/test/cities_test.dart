import 'package:amn_widgets/src/cities.dart';
import 'package:amn_widgets/src/routes.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('search matches names and countries, ignoring accents', () {
    expect(searchCities('ber').map((c) => c.name), contains('Berlin'));
    expect(searchCities('japan').single.name, 'Tokyo');
    expect(searchCities('sao').single.name, 'São Paulo');
    expect(searchCities('xyz'), isEmpty);
    expect(searchCities(''), hasLength(5));
  });

  test('routes only handle widget pages', () {
    expect(widgetConfigRoute(const RouteSettings(name: '/widget/weather?id=4&mode=add')), isNotNull);
    expect(widgetConfigRoute(const RouteSettings(name: '/widget/clock?id=4&mode=edit')), isNotNull);
    expect(widgetConfigRoute(const RouteSettings(name: '/')), isNull);
    expect(widgetConfigRoute(const RouteSettings(name: '/widget/other')), isNull);
  });
}
