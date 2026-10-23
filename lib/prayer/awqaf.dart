import 'dart:convert';

import 'package:http/http.dart' as http;

import 'timetable.dart';

/// Why a refresh failed, in words for the page.
class AwqafException implements Exception {
  const AwqafException(this.message);

  final String message;

  @override
  String toString() => message;
}

/// Awqaf's prayer-time API, the one awqaf.gov.ae/prayer-times calls.
/// Keep in step with tool/prayer/fetch.py.
class AwqafClient {
  AwqafClient({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  static final _url = Uri.parse('https://internationalprayertime.awqaf.gov.ae/api/v1/prayertimes');

  /// The public key the website itself sends. If Awqaf rotates it, refresh
  /// fails with a clear message and the bundled table stays in use.
  static const _key = 'ak_1148a6086913233acc33709fc9aa6e93';

  static const _fields = ['fajer', 'shuroq', 'dhohur', 'aser', 'maghreb', 'isha'];

  /// One leap year of [zone]'s official times, encoded like the bundled asset.
  Future<String> fetchZone(int zone) async {
    final http.Response r;
    try {
      r = await _client
          .post(
            _url,
            headers: {'Content-Type': 'application/json', 'Accept': 'application/json', 'Accept-Language': 'en', 'x-api-key': _key},
            body: jsonEncode({'start_date': '2028-01-01', 'end_date': '2028-12-31', 'official_id': zone}),
          )
          .timeout(const Duration(seconds: 30));
    } catch (_) {
      throw const AwqafException("Couldn't reach Awqaf. Check your connection.");
    }
    if (r.statusCode == 401 || r.statusCode == 403) {
      throw const AwqafException("Awqaf no longer accepts AMN's access key. An app update is needed to refresh.");
    }
    if (r.statusCode != 200) throw AwqafException('Awqaf answered with an error (${r.statusCode}).');
    return parse(r.body);
  }

  /// Encodes a 366-day response; throws if it isn't the shape the app knows.
  static String parse(String body) {
    try {
      final result = (jsonDecode(body) as Map<String, dynamic>)['result'] as Map<String, dynamic>;
      final days = result['prayer_times'] as List;
      final official = ((result['meta_data'] as Map)['method'] as Map)['official'] == true;
      final times = days.map((d) => _fields.map((f) => (d[f] as String).replaceAll(':', '')).join()).join();
      if (official && days.length == 366 && Timetable.isValid(times)) return times;
    } catch (_) {
      // Fall through: the format changed.
    }
    throw const AwqafException("Awqaf's data has changed format. An app update is needed to refresh.");
  }
}
