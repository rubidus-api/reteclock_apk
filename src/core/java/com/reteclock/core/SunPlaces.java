package com.reteclock.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Places to reckon the sun from, picked by region, then country, then city (issue #55, RFC-0015).
 *
 * <p>Capitals and large cities, so that somebody who does not know their coordinates can still get
 * a sunrise within a few minutes: at mid latitudes a degree of longitude is four minutes of sun, and
 * the coordinates here are to a hundredth of a degree. Anywhere else, the coordinates can be typed.
 *
 * <p>A city says nothing about the clock's offset and is not asked to: the time zone stays the
 * user's own setting.
 */
public final class SunPlaces {

    /** One city and where it is. */
    public static final class Place {
        public final String region;
        public final String country;
        public final String city;
        public final double latitude;
        public final double longitude;

        Place(String region, String country, String city, double latitude, double longitude) {
            this.region = region;
            this.country = country;
            this.city = city;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /** "Seoul, South Korea". */
        public String name() {
            return city + ", " + country;
        }
    }

    /** region | country | city | latitude | longitude */
    private static final String[] TABLE = {
        "Asia|South Korea|Seoul|37.57|126.98",
        "Asia|South Korea|Busan|35.18|129.08",
        "Asia|South Korea|Incheon|37.46|126.71",
        "Asia|South Korea|Daegu|35.87|128.60",
        "Asia|South Korea|Daejeon|36.35|127.38",
        "Asia|South Korea|Gwangju|35.16|126.85",
        "Asia|South Korea|Jeju|33.50|126.53",
        "Asia|North Korea|Pyongyang|39.02|125.75",
        "Asia|Japan|Tokyo|35.68|139.69",
        "Asia|Japan|Osaka|34.69|135.50",
        "Asia|Japan|Sapporo|43.06|141.35",
        "Asia|Japan|Fukuoka|33.59|130.40",
        "Asia|Japan|Naha|26.21|127.68",
        "Asia|China|Beijing|39.90|116.40",
        "Asia|China|Shanghai|31.23|121.47",
        "Asia|China|Guangzhou|23.13|113.26",
        "Asia|China|Chengdu|30.57|104.07",
        "Asia|China|Harbin|45.80|126.53",
        "Asia|China|Urumqi|43.83|87.62",
        "Asia|China|Kashgar|39.47|75.99",
        "Asia|China|Lhasa|29.65|91.17",
        "Asia|Hong Kong|Hong Kong|22.32|114.17",
        "Asia|Taiwan|Taipei|25.03|121.57",
        "Asia|Mongolia|Ulaanbaatar|47.89|106.91",
        "Asia|Philippines|Manila|14.60|120.98",
        "Asia|Vietnam|Hanoi|21.03|105.85",
        "Asia|Vietnam|Ho Chi Minh City|10.82|106.63",
        "Asia|Thailand|Bangkok|13.76|100.50",
        "Asia|Cambodia|Phnom Penh|11.56|104.92",
        "Asia|Laos|Vientiane|17.98|102.63",
        "Asia|Myanmar|Yangon|16.87|96.20",
        "Asia|Malaysia|Kuala Lumpur|3.14|101.69",
        "Asia|Singapore|Singapore|1.35|103.82",
        "Asia|Indonesia|Jakarta|-6.21|106.85",
        "Asia|Indonesia|Surabaya|-7.25|112.75",
        "Asia|Indonesia|Denpasar|-8.65|115.22",
        "Asia|Brunei|Bandar Seri Begawan|4.90|114.94",
        "Asia|India|New Delhi|28.61|77.21",
        "Asia|India|Mumbai|19.08|72.88",
        "Asia|India|Kolkata|22.57|88.36",
        "Asia|India|Chennai|13.08|80.27",
        "Asia|India|Bengaluru|12.97|77.59",
        "Asia|Pakistan|Karachi|24.86|67.01",
        "Asia|Pakistan|Lahore|31.55|74.34",
        "Asia|Pakistan|Islamabad|33.68|73.05",
        "Asia|Bangladesh|Dhaka|23.81|90.41",
        "Asia|Nepal|Kathmandu|27.72|85.32",
        "Asia|Sri Lanka|Colombo|6.93|79.86",
        "Asia|Kazakhstan|Almaty|43.24|76.95",
        "Asia|Kazakhstan|Astana|51.17|71.45",
        "Asia|Uzbekistan|Tashkent|41.30|69.24",
        "Asia|Afghanistan|Kabul|34.56|69.21",
        "Middle East|Iran|Tehran|35.69|51.39",
        "Middle East|Iran|Karaj|35.85|50.96"
        "Middle East|Iran|Mashhad|36.30|59.61",
        "Middle East|Iran|Isfahan|32.65|51.67",
        "Middle East|Iran|Tabriz|38.06|46.29"
        "Middle East|Iran|Shiraz|29.59|52.58",
        "Middle East|Iran|Kazerun|29.62|51.65"
        "Middle East|Iran|Qom|34.63|50.87"
        "Middle East|Iran|Bandar Abbas|27.18|56.27"
        "Middle East|Iran|Takestan|36.07|49.70"
        "Middle East|Iran|Yasuj|30.66|51.58"
        "Middle East|Iran|Aligudarz|33.42|49.69"
        "Middle East|Iran|Ahar|38.48|47.06"
        "Middle East|Iran|Ahvaz|31.31|48.67"
        "Middle East|Iraq|Baghdad|33.31|44.36",
        "Middle East|Saudi Arabia|Riyadh|24.71|46.68",
        "Middle East|Saudi Arabia|Jeddah|21.49|39.19",
        "Middle East|Saudi Arabia|Mecca|21.42|39.83",
        "Middle East|United Arab Emirates|Dubai|25.20|55.27",
        "Middle East|United Arab Emirates|Abu Dhabi|24.45|54.38",
        "Middle East|Qatar|Doha|25.29|51.53",
        "Middle East|Kuwait|Kuwait City|29.38|47.99",
        "Middle East|Oman|Muscat|23.59|58.41",
        "Middle East|Yemen|Sanaa|15.37|44.19",
        "Middle East|Jordan|Amman|31.95|35.93",
        "Middle East|Israel|Jerusalem|31.77|35.21",
        "Middle East|Israel|Tel Aviv|32.09|34.78",
        "Middle East|Lebanon|Beirut|33.89|35.50",
        "Middle East|Syria|Damascus|33.51|36.29",
        "Middle East|Turkey|Istanbul|41.01|28.98",
        "Middle East|Turkey|Ankara|39.93|32.86",
        "Middle East|Azerbaijan|Baku|40.41|49.87",
        "Middle East|Georgia|Tbilisi|41.72|44.79",
        "Middle East|Armenia|Yerevan|40.18|44.51",
        "Europe|United Kingdom|London|51.51|-0.13",
        "Europe|United Kingdom|Edinburgh|55.95|-3.19",
        "Europe|United Kingdom|Manchester|53.48|-2.24",
        "Europe|Ireland|Dublin|53.35|-6.26",
        "Europe|France|Paris|48.86|2.35",
        "Europe|France|Marseille|43.30|5.37",
        "Europe|France|Lyon|45.76|4.84",
        "Europe|Spain|Madrid|40.42|-3.70",
        "Europe|Spain|Barcelona|41.39|2.17",
        "Europe|Spain|Seville|37.39|-5.98",
        "Europe|Portugal|Lisbon|38.72|-9.14",
        "Europe|Italy|Rome|41.90|12.50",
        "Europe|Italy|Milan|45.46|9.19",
        "Europe|Italy|Naples|40.85|14.27",
        "Europe|Germany|Berlin|52.52|13.40",
        "Europe|Germany|Munich|48.14|11.58",
        "Europe|Germany|Hamburg|53.55|9.99",
        "Europe|Germany|Frankfurt|50.11|8.68",
        "Europe|Netherlands|Amsterdam|52.37|4.90",
        "Europe|Belgium|Brussels|50.85|4.35",
        "Europe|Switzerland|Zurich|47.38|8.54",
        "Europe|Switzerland|Geneva|46.20|6.14",
        "Europe|Austria|Vienna|48.21|16.37",
        "Europe|Czechia|Prague|50.08|14.44",
        "Europe|Poland|Warsaw|52.23|21.01",
        "Europe|Hungary|Budapest|47.50|19.04",
        "Europe|Romania|Bucharest|44.43|26.10",
        "Europe|Bulgaria|Sofia|42.70|23.32",
        "Europe|Greece|Athens|37.98|23.73",
        "Europe|Serbia|Belgrade|44.79|20.45",
        "Europe|Croatia|Zagreb|45.81|15.98",
        "Europe|Denmark|Copenhagen|55.68|12.57",
        "Europe|Norway|Oslo|59.91|10.75",
        "Europe|Norway|Tromso|69.65|18.96",
        "Europe|Sweden|Stockholm|59.33|18.07",
        "Europe|Finland|Helsinki|60.17|24.94",
        "Europe|Iceland|Reykjavik|64.15|-21.94",
        "Europe|Estonia|Tallinn|59.44|24.75",
        "Europe|Latvia|Riga|56.95|24.11",
        "Europe|Lithuania|Vilnius|54.69|25.28",
        "Europe|Ukraine|Kyiv|50.45|30.52",
        "Europe|Belarus|Minsk|53.90|27.57",
        "Europe|Russia|Moscow|55.76|37.62",
        "Europe|Russia|Saint Petersburg|59.93|30.34",
        "Asia|Russia|Novosibirsk|55.01|82.93",
        "Asia|Russia|Yekaterinburg|56.84|60.60",
        "Asia|Russia|Vladivostok|43.12|131.89",
        "Asia|Russia|Yakutsk|62.03|129.73",
        "Africa|Egypt|Cairo|30.04|31.24",
        "Africa|Egypt|Alexandria|31.20|29.92",
        "Africa|Morocco|Casablanca|33.57|-7.59",
        "Africa|Morocco|Rabat|34.02|-6.84",
        "Africa|Algeria|Algiers|36.75|3.06",
        "Africa|Tunisia|Tunis|36.81|10.18",
        "Africa|Libya|Tripoli|32.89|13.19",
        "Africa|Sudan|Khartoum|15.50|32.56",
        "Africa|Ethiopia|Addis Ababa|9.03|38.74",
        "Africa|Kenya|Nairobi|-1.29|36.82",
        "Africa|Tanzania|Dar es Salaam|-6.79|39.21",
        "Africa|Uganda|Kampala|0.35|32.58",
        "Africa|Nigeria|Lagos|6.52|3.38",
        "Africa|Nigeria|Abuja|9.08|7.40",
        "Africa|Ghana|Accra|5.60|-0.19",
        "Africa|Senegal|Dakar|14.72|-17.47",
        "Africa|Ivory Coast|Abidjan|5.36|-4.01",
        "Africa|DR Congo|Kinshasa|-4.44|15.27",
        "Africa|Angola|Luanda|-8.84|13.23",
        "Africa|South Africa|Johannesburg|-26.20|28.05",
        "Africa|South Africa|Cape Town|-33.92|18.42",
        "Africa|Zimbabwe|Harare|-17.83|31.05",
        "Africa|Madagascar|Antananarivo|-18.88|47.51",
        "North America|United States|New York|40.71|-74.01",
        "North America|United States|Washington|38.91|-77.04",
        "North America|United States|Boston|42.36|-71.06",
        "North America|United States|Miami|25.76|-80.19",
        "North America|United States|Atlanta|33.75|-84.39",
        "North America|United States|Chicago|41.88|-87.63",
        "North America|United States|Houston|29.76|-95.37",
        "North America|United States|Dallas|32.78|-96.80",
        "North America|United States|Denver|39.74|-104.99",
        "North America|United States|Phoenix|33.45|-112.07",
        "North America|United States|Los Angeles|34.05|-118.24",
        "North America|United States|San Francisco|37.77|-122.42",
        "North America|United States|Seattle|47.61|-122.33",
        "North America|United States|Anchorage|61.22|-149.90",
        "North America|United States|Honolulu|21.31|-157.86",
        "North America|Canada|Toronto|43.65|-79.38",
        "North America|Canada|Montreal|45.50|-73.57",
        "North America|Canada|Ottawa|45.42|-75.70",
        "North America|Canada|Vancouver|49.28|-123.12",
        "North America|Canada|Calgary|51.05|-114.07",
        "North America|Canada|Halifax|44.65|-63.57",
        "North America|Mexico|Mexico City|19.43|-99.13",
        "North America|Mexico|Guadalajara|20.66|-103.35",
        "North America|Mexico|Monterrey|25.69|-100.32",
        "Central America and Caribbean|Guatemala|Guatemala City|14.63|-90.51",
        "Central America and Caribbean|Costa Rica|San Jose|9.93|-84.08",
        "Central America and Caribbean|Panama|Panama City|8.98|-79.52",
        "Central America and Caribbean|Cuba|Havana|23.11|-82.37",
        "Central America and Caribbean|Dominican Republic|Santo Domingo|18.49|-69.93",
        "Central America and Caribbean|Jamaica|Kingston|18.02|-76.80",
        "Central America and Caribbean|Puerto Rico|San Juan|18.47|-66.11",
        "South America|Brazil|Sao Paulo|-23.55|-46.63",
        "South America|Brazil|Rio de Janeiro|-22.91|-43.17",
        "South America|Brazil|Brasilia|-15.79|-47.88",
        "South America|Brazil|Manaus|-3.12|-60.02",
        "South America|Argentina|Buenos Aires|-34.60|-58.38",
        "South America|Chile|Santiago|-33.45|-70.67",
        "South America|Peru|Lima|-12.05|-77.04",
        "South America|Colombia|Bogota|4.71|-74.07",
        "South America|Venezuela|Caracas|10.48|-66.90",
        "South America|Ecuador|Quito|-0.18|-78.47",
        "South America|Bolivia|La Paz|-16.50|-68.15",
        "South America|Uruguay|Montevideo|-34.90|-56.16",
        "South America|Paraguay|Asuncion|-25.26|-57.58",
        "Oceania|Australia|Sydney|-33.87|151.21",
        "Oceania|Australia|Melbourne|-37.81|144.96",
        "Oceania|Australia|Brisbane|-27.47|153.03",
        "Oceania|Australia|Perth|-31.95|115.86",
        "Oceania|Australia|Adelaide|-34.93|138.60",
        "Oceania|Australia|Darwin|-12.46|130.84",
        "Oceania|New Zealand|Auckland|-36.85|174.76",
        "Oceania|New Zealand|Wellington|-41.29|174.78",
        "Oceania|Fiji|Suva|-18.14|178.44",
        "Oceania|Papua New Guinea|Port Moresby|-9.44|147.18",
        "Oceania|Guam|Hagatna|13.48|144.75",
        "Antarctica|Antarctica|McMurdo Station|-77.85|166.67"
    };

    private static List<Place> places;

    private SunPlaces() {
    }

    /** Every place, in the table's order. */
    public static synchronized List<Place> all() {
        if (places == null) {
            List<Place> out = new ArrayList<Place>();
            for (String row : TABLE) {
                String[] f = row.split("\\|");
                out.add(new Place(f[0], f[1], f[2], Double.parseDouble(f[3]),
                        Double.parseDouble(f[4])));
            }
            places = Collections.unmodifiableList(out);
        }
        return places;
    }

    /** The regions, in the order they first appear. */
    public static List<String> regions() {
        List<String> out = new ArrayList<String>();
        for (Place p : all()) {
            if (!out.contains(p.region)) {
                out.add(p.region);
            }
        }
        return out;
    }

    /** The countries of a region, alphabetically. */
    public static List<String> countries(String region) {
        List<String> out = new ArrayList<String>();
        for (Place p : all()) {
            if (p.region.equals(region) && !out.contains(p.country)) {
                out.add(p.country);
            }
        }
        Collections.sort(out);
        return out;
    }

    /** The cities of a country within a region, alphabetically. */
    public static List<Place> cities(String region, String country) {
        List<Place> out = new ArrayList<Place>();
        for (Place p : all()) {
            if (p.region.equals(region) && p.country.equals(country)) {
                out.add(p);
            }
        }
        Collections.sort(out, new java.util.Comparator<Place>() {
            @Override
            public int compare(Place a, Place b) {
                return a.city.compareTo(b.city);
            }
        });
        return out;
    }
}
