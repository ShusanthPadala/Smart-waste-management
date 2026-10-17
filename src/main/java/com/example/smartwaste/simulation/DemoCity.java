package com.example.smartwaste.simulation;

import com.example.smartwaste.entity.SiteProfile;
import com.example.smartwaste.entity.WasteType;

import java.util.List;
import java.util.Optional;

import static com.example.smartwaste.entity.SiteProfile.*;
import static com.example.smartwaste.entity.WasteType.*;

/**
 * Ready-made demo districts. Each has a depot and 24 bins whose {@code targetFill} is where the bin
 * stands when the demo is loaded, chosen so a few bins need pickup straight away.
 */
public record DemoCity(String key, String name, double depotLatitude, double depotLongitude, List<Site> sites) {

    public record Site(String code, String name, String zone, WasteType type, double lat, double lon,
                       double capacity, SiteProfile profile, double baseRate, double targetFill) {}

    public static final DemoCity VISAKHAPATNAM = new DemoCity("visakhapatnam", "Visakhapatnam", 17.7345, 83.2985, List.of(
            new Site("B001", "MVP Colony Sector 6", "MVP Colony", GENERAL, 17.7406, 83.3332, 1100, RESIDENTIAL, 1.1, 42),
            new Site("B002", "Seethammadhara North Extension", "Seethammadhara", GENERAL, 17.7445, 83.3115, 1100, RESIDENTIAL, 1.3, 63),
            new Site("B003", "Waltair Uplands", "Beach Road", RECYCLABLE, 17.7200, 83.3260, 660, RESIDENTIAL, 0.9, 28),
            new Site("B004", "Poorna Market", "One Town", ORGANIC, 17.7065, 83.2985, 2400, MARKET, 2.6, 95),
            new Site("B005", "Dwaraka Bus Station", "Dwaraka Nagar", GENERAL, 17.7255, 83.3040, 1100, TRANSIT, 2.0, 91),
            new Site("B006", "Jagadamba Junction", "One Town", ORGANIC, 17.7116, 83.3016, 1100, COMMERCIAL, 2.2, 82),
            new Site("B007", "MVP Rythu Bazaar", "MVP Colony", ORGANIC, 17.7440, 83.3300, 2400, MARKET, 2.8, 97),
            new Site("B008", "Kailasagiri Ropeway Base", "MVP Colony", GENERAL, 17.7490, 83.3420, 660, PARK, 0.8, 35),
            new Site("B009", "VUDA Park", "Beach Road", RECYCLABLE, 17.7230, 83.3360, 1100, PARK, 1.0, 22),
            new Site("B010", "Siripuram Junction", "Dwaraka Nagar", RECYCLABLE, 17.7210, 83.3170, 1100, COMMERCIAL, 1.6, 71),
            new Site("B011", "CMR Central Mall", "Seethammadhara", RECYCLABLE, 17.7365, 83.3195, 1100, COMMERCIAL, 1.5, 48),
            new Site("B012", "RK Beach Food Court", "Beach Road", ORGANIC, 17.7142, 83.3236, 1100, MARKET, 2.3, 79),
            new Site("B013", "Akkayyapalem Main Road", "West Vizag", GENERAL, 17.7300, 83.2950, 1100, RESIDENTIAL, 1.2, 57),
            new Site("B014", "Kancharapalem Colony", "West Vizag", GENERAL, 17.7380, 83.2800, 660, RESIDENTIAL, 1.0, 18),
            new Site("B015", "Visakhapatnam Railway Station", "One Town", GENERAL, 17.7215, 83.2900, 1100, TRANSIT, 1.8, 66),
            new Site("B016", "Lawsons Bay Beach Walk", "Beach Road", RECYCLABLE, 17.7330, 83.3380, 660, PARK, 1.1, 52),
            new Site("B017", "Maddilapalem Junction", "Seethammadhara", GENERAL, 17.7370, 83.3220, 1100, TRANSIT, 1.7, 39),
            new Site("B018", "Gurudwara Junction", "Dwaraka Nagar", GENERAL, 17.7330, 83.3080, 1100, TRANSIT, 1.9, 87),
            new Site("B019", "NAD Junction", "West Vizag", GENERAL, 17.7430, 83.2350, 1100, TRANSIT, 1.6, 44),
            new Site("B020", "Madhurawada Bus Stop", "Madhurawada", GENERAL, 17.8150, 83.3550, 1100, TRANSIT, 1.5, 61),
            new Site("B021", "Isukathota", "MVP Colony", GENERAL, 17.7480, 83.3250, 1100, RESIDENTIAL, 1.2, 33),
            new Site("B022", "Andhra University Hostels", "Seethammadhara", ORGANIC, 17.7290, 83.3210, 1100, RESIDENTIAL, 1.4, 74),
            new Site("B023", "Pandurangapuram", "Beach Road", RECYCLABLE, 17.7180, 83.3320, 660, RESIDENTIAL, 1.0, 12),
            new Site("B024", "Asilmetta Recycling Point", "Dwaraka Nagar", RECYCLABLE, 17.7240, 83.3110, 2400, COMMERCIAL, 1.4, 55)
    ));

    public static final DemoCity HYDERABAD = new DemoCity("hyderabad", "Hyderabad", 17.4401, 78.3489, List.of(
            new Site("B001", "Green Park Residency", "Gachibowli", GENERAL, 17.4423, 78.3560, 1100, RESIDENTIAL, 1.1, 42),
            new Site("B002", "Sector 12 Apartments", "Kondapur", GENERAL, 17.4615, 78.3647, 1100, RESIDENTIAL, 1.3, 63),
            new Site("B003", "Lakeview Society", "Khajaguda", RECYCLABLE, 17.4150, 78.3800, 660, RESIDENTIAL, 0.9, 28),
            new Site("B004", "Community Market", "Madhapur", ORGANIC, 17.4483, 78.3915, 2400, MARKET, 2.6, 95),
            new Site("B005", "HITEC City Metro Plaza", "HITEC City", GENERAL, 17.4474, 78.3762, 1100, TRANSIT, 2.0, 91),
            new Site("B006", "Cyber Towers Food Court", "HITEC City", ORGANIC, 17.4504, 78.3812, 1100, COMMERCIAL, 2.2, 82),
            new Site("B007", "Kondapur Vegetable Market", "Kondapur", ORGANIC, 17.4632, 78.3555, 2400, MARKET, 2.8, 97),
            new Site("B008", "Botanical Garden Gate", "Kondapur", GENERAL, 17.4589, 78.3557, 660, PARK, 0.8, 35),
            new Site("B009", "Gachibowli Stadium", "Gachibowli", RECYCLABLE, 17.4467, 78.3475, 1100, PARK, 1.0, 22),
            new Site("B010", "DLF Cyber City", "Gachibowli", RECYCLABLE, 17.4486, 78.3637, 1100, COMMERCIAL, 1.6, 71),
            new Site("B011", "Nanakramguda Tech Park", "Financial District", RECYCLABLE, 17.4198, 78.3440, 1100, COMMERCIAL, 1.5, 48),
            new Site("B012", "Financial District Food Street", "Financial District", ORGANIC, 17.4147, 78.3417, 1100, MARKET, 2.3, 79),
            new Site("B013", "Manikonda Main Road", "Manikonda", GENERAL, 17.4040, 78.3860, 1100, RESIDENTIAL, 1.2, 57),
            new Site("B014", "Puppalaguda Colony", "Manikonda", GENERAL, 17.3985, 78.3735, 660, RESIDENTIAL, 1.0, 18),
            new Site("B015", "Jubilee Hills Check Post", "Jubilee Hills", GENERAL, 17.4300, 78.4080, 1100, TRANSIT, 1.8, 66),
            new Site("B016", "Durgam Cheruvu Lake Walk", "Madhapur", RECYCLABLE, 17.4300, 78.3880, 660, PARK, 1.1, 52),
            new Site("B017", "Kothaguda Junction", "Kondapur", GENERAL, 17.4610, 78.3700, 1100, TRANSIT, 1.7, 39),
            new Site("B018", "Raidurg Metro Station", "HITEC City", GENERAL, 17.4402, 78.3830, 1100, TRANSIT, 1.9, 87),
            new Site("B019", "Lingampally Bus Depot", "Lingampally", GENERAL, 17.4840, 78.3170, 1100, TRANSIT, 1.6, 44),
            new Site("B020", "Hafeezpet Railway Station", "Hafeezpet", GENERAL, 17.4790, 78.3560, 1100, TRANSIT, 1.5, 61),
            new Site("B021", "Indira Nagar", "Gachibowli", GENERAL, 17.4370, 78.3550, 1100, RESIDENTIAL, 1.2, 33),
            new Site("B022", "University Hostel Block", "Gachibowli", ORGANIC, 17.4565, 78.3280, 1100, RESIDENTIAL, 1.4, 74),
            new Site("B023", "Whitefields Residency", "Kondapur", RECYCLABLE, 17.4570, 78.3700, 660, RESIDENTIAL, 1.0, 12),
            new Site("B024", "Gachibowli Recycling Point", "Gachibowli", RECYCLABLE, 17.4400, 78.3640, 2400, COMMERCIAL, 1.4, 55)
    ));

    public static final List<DemoCity> ALL = List.of(VISAKHAPATNAM, HYDERABAD);

    public static Optional<DemoCity> byKey(String key) {
        return ALL.stream().filter(c -> c.key().equalsIgnoreCase(key)).findFirst();
    }
}
