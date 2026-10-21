/// Sample cities for the edit pages.
/// TODO: replace with Open-Meteo Geocoding search (2+ characters, 300 ms debounce, 5 results).
class City {
  const City(this.name, this.country, this.lat, this.lon, [this.timeZone]);

  final String name;
  final String country;
  final double lat;
  final double lon;
  final String? timeZone;
}

const kCities = <City>[
  City('Berlin', 'Germany', 52.52, 13.41, 'Europe/Berlin'),
  City('Hamburg', 'Germany', 53.55, 9.99, 'Europe/Berlin'),
  City('Munich', 'Germany', 48.14, 11.58, 'Europe/Berlin'),
  City('London', 'United Kingdom', 51.51, -0.13, 'Europe/London'),
  City('Paris', 'France', 48.86, 2.35, 'Europe/Paris'),
  City('Riyadh', 'Saudi Arabia', 24.71, 46.68, 'Asia/Riyadh'),
  City('Jeddah', 'Saudi Arabia', 21.49, 39.19, 'Asia/Riyadh'),
  City('Dubai', 'United Arab Emirates', 25.20, 55.27, 'Asia/Dubai'),
  City('Karachi', 'Pakistan', 24.86, 67.01, 'Asia/Karachi'),
  City('Mumbai', 'India', 19.08, 72.88, 'Asia/Kolkata'),
  City('Singapore', 'Singapore', 1.35, 103.82, 'Asia/Singapore'),
  City('Tokyo', 'Japan', 35.68, 139.69, 'Asia/Tokyo'),
  City('Sydney', 'Australia', -33.87, 151.21, 'Australia/Sydney'),
  City('Cairo', 'Egypt', 30.04, 31.24, 'Africa/Cairo'),
  City('New York', 'United States', 40.71, -74.01, 'America/New_York'),
  City('Toronto', 'Canada', 43.65, -79.38, 'America/Toronto'),
  City('Los Angeles', 'United States', 34.05, -118.24, 'America/Los_Angeles'),
  City('São Paulo', 'Brazil', -23.55, -46.63, 'America/Sao_Paulo'),
];

String _fold(String s) => s
    .toLowerCase()
    .replaceAll(RegExp('[àáâãä]'), 'a')
    .replaceAll(RegExp('[èéêë]'), 'e')
    .replaceAll(RegExp('[ìíîï]'), 'i')
    .replaceAll(RegExp('[òóôõö]'), 'o')
    .replaceAll(RegExp('[ùúûü]'), 'u');

/// Cities whose name or country starts with [query]; the first [limit] when empty.
List<City> searchCities(String query, {int limit = 5}) {
  final q = _fold(query.trim());
  return kCities.where((c) => q.isEmpty || _fold(c.name).startsWith(q) || _fold(c.country).startsWith(q)).take(limit).toList();
}
