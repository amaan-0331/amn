import 'package:amn_widgets/src/geocoding.dart';
import 'package:amn_widgets/src/routes.dart';
import 'package:amn_widgets/src/ui.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _body = '''
{"results":[
 {"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"country":"Germany","admin1":"Land Berlin","timezone":"Europe/Berlin"},
 {"id":5083330,"name":"Berlin","latitude":44.46867,"longitude":-71.18508,"country":"United States","admin1":"New Hampshire","timezone":"America/New_York"}
],"generationtime_ms":0.5}
''';

void main() {
  test('parses geocoding results', () {
    final cities = Geocoding.parseResults(_body);
    expect(cities, hasLength(2));
    expect(cities.first.timeZone, 'Europe/Berlin');
    expect(cities.last.subtitle, 'New Hampshire, United States');
    expect(Geocoding.parseResults('{"generationtime_ms":0.3}'), isEmpty);
  });

  test('search builds the request and reports problems', () async {
    late Uri seen;
    final ok = Geocoding(client: MockClient((r) async {
      seen = r.url;
      return http.Response(_body, 200);
    }));
    expect((await ok.search(' ber ')).cities, hasLength(2));
    expect(seen.host, 'geocoding-api.open-meteo.com');
    expect(seen.queryParameters['name'], 'ber');
    expect(seen.queryParameters['count'], '5');

    final down = Geocoding(client: MockClient((r) async => http.Response('oops', 500)));
    expect((await down.search('ber')).problem, SearchProblem.failed);

    final offline = Geocoding(client: MockClient((r) async => throw http.ClientException('no network')));
    expect((await offline.search('ber')).problem, SearchProblem.offline);
  });

  test('updated label', () {
    final now = DateTime(2026, 10, 21, 14, 0);
    int ago(Duration d) => now.subtract(d).millisecondsSinceEpoch;
    expect(updatedLabel(null, now: now), 'Not updated yet');
    expect(updatedLabel(ago(const Duration(seconds: 20)), now: now), 'Updated just now');
    expect(updatedLabel(ago(const Duration(minutes: 12)), now: now), 'Updated 12 min ago');
    expect(updatedLabel(ago(const Duration(hours: 3, minutes: 40)), now: now), 'Updated 3 h ago');
  });

  test('routes only handle widget pages', () {
    expect(widgetConfigRoute(const RouteSettings(name: '/widget/weather?id=4&mode=add')), isNotNull);
    expect(widgetConfigRoute(const RouteSettings(name: '/widget/clock?id=4&mode=edit')), isNotNull);
    expect(widgetConfigRoute(const RouteSettings(name: '/')), isNull);
    expect(widgetConfigRoute(const RouteSettings(name: '/widget/other')), isNull);
  });
}
