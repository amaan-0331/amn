import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

/// A place from the Open-Meteo Geocoding API.
class City {
  const City({required this.name, required this.country, required this.lat, required this.lon, this.region, this.timeZone});

  final String name;
  final String country;
  final String? region;
  final double lat;
  final double lon;
  final String? timeZone;

  /// "Bavaria, Germany", or just the country.
  String get subtitle => [if (region != null && region != name) region, country].join(', ');
}

/// Why a search returned nothing useful.
enum SearchProblem { offline, failed }

class SearchResult {
  const SearchResult(this.cities, [this.problem]);

  final List<City> cities;
  final SearchProblem? problem;
}

/// Open-Meteo Geocoding search. Free, no key.
class Geocoding {
  Geocoding({http.Client? client}) : _client = client ?? http.Client();

  final http.Client _client;

  static Uri searchUri(String query, {int count = 5}) => Uri.https('geocoding-api.open-meteo.com', '/v1/search', {
        'name': query.trim(),
        'count': '$count',
        'language': 'en',
        'format': 'json',
      });

  Future<SearchResult> search(String query) async {
    try {
      final res = await _client.get(searchUri(query)).timeout(const Duration(seconds: 10));
      if (res.statusCode != 200) return const SearchResult([], SearchProblem.failed);
      return SearchResult(parseResults(res.body));
    } on SocketException {
      return const SearchResult([], SearchProblem.offline);
    } on TimeoutException {
      return const SearchResult([], SearchProblem.offline);
    } on http.ClientException {
      return const SearchResult([], SearchProblem.offline);
    }
  }

  /// Parses the API body. No "results" key means no match.
  static List<City> parseResults(String body) {
    final json = jsonDecode(body) as Map<String, Object?>;
    final results = json['results'] as List<Object?>? ?? const [];
    return [
      for (final r in results.cast<Map<String, Object?>>())
        City(
          name: r['name']! as String,
          country: r['country'] as String? ?? '',
          region: r['admin1'] as String?,
          lat: (r['latitude']! as num).toDouble(),
          lon: (r['longitude']! as num).toDouble(),
          timeZone: r['timezone'] as String?,
        ),
    ];
  }
}
