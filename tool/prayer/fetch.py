# Usage: python3 tool/prayer/fetch.py
"""Builds the bundled UAE prayer timetable from Awqaf
(the General Authority of Islamic Affairs, Endowments and Zakat), the source
behind awqaf.gov.ae/prayer-times.

Awqaf's table is fixed by day of year: every year is the same, and 29 Feb
repeats 28 Feb. So one leap year per zone covers every date. Areas (the site's
neighbourhood list) come from areas.tsv, which maps each to its timetable zone.

The asset stores each zone as one string, 366 days x 6 times (Fajr, Sunrise,
Dhuhr, Asr, Maghrib, Isha) x HHMM. The app's Refresh does the same fetch; keep
API_URL, API_KEY and the encoding in step with Awqaf.kt in amn_widgets.

The file is an Android asset of amn_widgets, so the home-screen widgets read it
natively; the app gets it through the amn/widgets channel."""
import csv, datetime, json, os, sys, urllib.request

API_URL = 'https://internationalprayertime.awqaf.gov.ae/api/v1/prayertimes'
API_KEY = 'ak_1148a6086913233acc33709fc9aa6e93'  # the public key awqaf.gov.ae's own page sends
YEAR = 2028  # any leap year
FIELDS = ('fajer', 'shuroq', 'dhohur', 'aser', 'maghreb', 'isha')
EMIRATES = {1: 'Abu Dhabi', 2: 'Dubai', 3: 'Sharjah', 4: 'Ajman', 5: 'Umm Al Quwain', 6: 'Ras Al Khaimah', 7: 'Fujairah'}

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'packages', 'amn_widgets', 'android', 'src', 'main', 'assets', 'prayer', 'uae.json')


def fetch(zone):
    body = json.dumps({'start_date': f'{YEAR}-01-01', 'end_date': f'{YEAR}-12-31', 'official_id': zone}).encode()
    req = urllib.request.Request(API_URL, body, {
        'Content-Type': 'application/json', 'Accept': 'application/json', 'Accept-Language': 'en', 'x-api-key': API_KEY,
    })
    with urllib.request.urlopen(req, timeout=60) as r:
        res = json.load(r)['result']
    days = res['prayer_times']
    if len(days) != 366 or not res['meta_data']['method']['official']:
        sys.exit(f'zone {zone}: expected 366 official days, got {len(days)}')
    times = ''.join(d[f].replace(':', '') for d in days for f in FIELDS)
    if len(times) != 366 * 24 or not times.isdigit():
        sys.exit(f'zone {zone}: unexpected time format')
    # "Dubai - Hatta" -> "Hatta"
    name = res['meta_data']['location']['city'].split(' - ', 1)[-1]
    return name, times


def main():
    with open(os.path.join(HERE, 'areas.tsv'), encoding='utf-8') as f:
        areas = [[int(r['emirate']), int(r['area']), int(r['zone']), r['name'], r['name_ar']] for r in csv.DictReader(f, delimiter='\t')]
    zones = {}
    for z in sorted({a[2] for a in areas}):
        name, times = fetch(z)
        zones[str(z)] = {'name': name, 'times': times}
        print(f'{z:>3} {name}', file=sys.stderr)
    out = {
        'source': 'General Authority of Islamic Affairs, Endowments and Zakat (awqaf.gov.ae)',
        'fetched': datetime.date.today().isoformat(),
        'fields': ['fajr', 'sunrise', 'dhuhr', 'asr', 'maghrib', 'isha'],
        'emirates': {str(k): v for k, v in EMIRATES.items()},
        'zones': zones,
        'areas': areas,
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    print(f'{len(zones)} zones, {len(areas)} areas -> {os.path.relpath(OUT)}', file=sys.stderr)


if __name__ == '__main__':
    main()
