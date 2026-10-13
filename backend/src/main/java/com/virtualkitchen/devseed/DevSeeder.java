package com.virtualkitchen.devseed;

import com.virtualkitchen.auth.user.Role;
import com.virtualkitchen.auth.user.User;
import com.virtualkitchen.auth.user.UserRepository;
import com.virtualkitchen.auth.user.UserStatus;
import com.virtualkitchen.kitchen.KitchenStationService;
import com.virtualkitchen.kitchen.chef.ChefService;
import com.virtualkitchen.kitchen.chef.dto.CreateChefRequest;
import com.virtualkitchen.kitchen.consent.ChefConsentService;
import com.virtualkitchen.kitchen.consent.dto.ConsentResponse;
import com.virtualkitchen.kitchen.dto.StationRequest;
import com.virtualkitchen.kitchen.dto.StationResponse;
import com.virtualkitchen.menu.MenuService;
import com.virtualkitchen.menu.dto.MenuCategoryRequest;
import com.virtualkitchen.menu.dto.MenuItemRequest;
import com.virtualkitchen.restaurant.OrderType;
import com.virtualkitchen.restaurant.RestaurantService;
import com.virtualkitchen.restaurant.RestaurantStatus;
import com.virtualkitchen.restaurant.dto.OpeningSlotDto;
import com.virtualkitchen.restaurant.dto.RestaurantRequest;
import com.virtualkitchen.restaurant.dto.RestaurantResponse;
import com.virtualkitchen.restaurant.table.DiningTableRequest;
import com.virtualkitchen.restaurant.table.DiningTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Profile {@code dev-seed}: a ready-to-use demo restaurant for trying the product in a browser. Everything is created
 * only if missing, so restarts are safe. All accounts share {@code DEV_SEED_PASSWORD}. Refuses to run next to a
 * production profile; never enable it on a shared or production database.
 */
@Component
@Profile("dev-seed")
public class DevSeeder implements ApplicationRunner {

    /** Fixed so the web app's dev role switcher can log in with them. Keep in sync with web/lib/dev/accounts.ts. */
    public static final String ADMIN_PHONE = "+919000000001";
    public static final String OWNER_PHONE = "+919000000002";
    public static final String CHEF_1_PHONE = "+919000000003";
    public static final String CHEF_2_PHONE = "+919000000004";
    public static final String CUSTOMER_PHONE = "+919000000005";

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);
    private static final Set<String> FORBIDDEN_PROFILES = Set.of("prod", "production", "staging");

    private final Environment environment;
    private final String password;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final RestaurantService restaurants;
    private final MenuService menu;
    private final DiningTableService tables;
    private final KitchenStationService stations;
    private final ChefService chefs;
    private final ChefConsentService consents;

    public DevSeeder(Environment environment, @Value("${app.dev-seed.password:}") String password, UserRepository users,
                     PasswordEncoder passwordEncoder, RestaurantService restaurants, MenuService menu, DiningTableService tables,
                     KitchenStationService stations, ChefService chefs, ChefConsentService consents) {
        this.environment = environment;
        this.password = password;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.restaurants = restaurants;
        this.menu = menu;
        this.tables = tables;
        this.stations = stations;
        this.chefs = chefs;
        this.consents = consents;
    }

    @Override
    public void run(ApplicationArguments args) {
        requireDevelopment(environment, password);

        User admin = user("Dev Admin", ADMIN_PHONE, Role.ADMIN);
        User owner = user("Dev Owner", OWNER_PHONE, Role.RESTAURANT_OWNER);
        User customer = user("Dev Customer", CUSTOMER_PHONE, Role.CUSTOMER);

        RestaurantResponse restaurant = restaurants.listOwned(owner.getId()).stream().findFirst()
                .orElseGet(() -> restaurants.create(owner.getId(), new RestaurantRequest(
                        "Dev Spice Kitchen", "dev-spice-kitchen", "12 MG Road", "Bengaluru", 12.9756, 77.6050,
                        List.of("North Indian", "Biryani"),
                        Arrays.stream(DayOfWeek.values()).map(d -> new OpeningSlotDto(d, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT)).toList(),
                        Set.of(OrderType.DINE_IN, OrderType.TAKEAWAY, OrderType.DELIVERY), 5.0, null)));
        UUID rid = restaurant.id();
        if (restaurant.status() != RestaurantStatus.ACTIVE) {
            restaurants.changeStatusAsAdmin(rid, RestaurantStatus.ACTIVE);
        }
        if (menu.ownerMenu(owner.getId(), rid).isEmpty()) {
            seedMenu(owner.getId(), rid);
        }
        if (tables.list(owner.getId(), rid).isEmpty()) {
            for (String label : List.of("T1", "T2", "T3")) {
                tables.create(owner.getId(), rid, new DiningTableRequest(label));
            }
        }
        List<StationResponse> stationList = stations.list(owner.getId(), rid);
        if (stationList.isEmpty()) {
            stations.create(owner.getId(), rid, new StationRequest("Tandoor"));
            stations.create(owner.getId(), rid, new StationRequest("Curry"));
            stationList = stations.list(owner.getId(), rid);
        }
        if (chefs.list(owner.getId(), rid).isEmpty()) {
            seedChef(owner.getId(), rid, "Ravi Kumar", "Chef Ravi", CHEF_1_PHONE, stationId(stationList, "Tandoor"));
            seedChef(owner.getId(), rid, "Meera Iyer", "Chef Meera", CHEF_2_PHONE, stationId(stationList, "Curry"));
        }

        String tableTokens = String.join("\n", tables.list(owner.getId(), rid).stream()
                .map(t -> String.format("    %-3s %s", t.label(), t.qrUrl())).toList());
        log.warn("""

                ===================== DEV SEED (profile dev-seed; never use in production) =====================
                  Password for every account below: {}
                  ADMIN      {}  ({})
                  OWNER      {}  ({})   restaurant "{}" /r/{}
                  CHEF       {}  (Chef Ravi, station Tandoor)
                  CHEF       {}  (Chef Meera, station Curry)
                  CUSTOMER   {}  ({})
                  Table QR links:
                {}
                ================================================================================================
                """, password, admin.getPhone(), admin.getName(), owner.getPhone(), owner.getName(), restaurant.name(),
                restaurant.slug(), CHEF_1_PHONE, CHEF_2_PHONE, customer.getPhone(), customer.getName(), tableTokens);
    }

    static void requireDevelopment(Environment environment, String password) {
        if (environment.acceptsProfiles(Profiles.of(FORBIDDEN_PROFILES.toArray(String[]::new)))) {
            throw new IllegalStateException("The dev-seed profile must never run together with " + FORBIDDEN_PROFILES);
        }
        if (password == null || password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("dev-seed needs DEV_SEED_PASSWORD (12–72 characters)");
        }
    }

    private User user(String name, String phone, Role role) {
        return users.findByPhone(phone).orElseGet(() ->
                users.save(new User(name, phone, null, passwordEncoder.encode(password), role, UserStatus.ACTIVE)));
    }

    private static UUID stationId(List<StationResponse> stations, String name) {
        return stations.stream().filter(s -> s.name().equals(name)).findFirst()
                .orElse(stations.getFirst()).id();
    }

    private void seedMenu(UUID ownerId, UUID rid) {
        UUID starters = menu.createCategory(ownerId, rid, new MenuCategoryRequest("Starters", "From the tandoor", 0)).id();
        UUID mains = menu.createCategory(ownerId, rid, new MenuCategoryRequest("Mains", "Curries and rice", 1)).id();
        record Item(UUID category, String name, String description, long pricePaise, boolean veg) {
        }
        List<Item> items = List.of(
                new Item(starters, "Paneer Tikka", "Char-grilled cottage cheese", 24900, true),
                new Item(starters, "Chicken Tikka", "Smoky yoghurt-marinated chicken", 29900, false),
                new Item(starters, "Hara Bhara Kebab", "Spinach and pea patties", 19900, true),
                new Item(starters, "Tandoori Prawns", "Ajwain and lemon", 44900, false),
                new Item(mains, "Dal Makhani", "Slow-cooked black lentils", 22900, true),
                new Item(mains, "Butter Chicken", "Tomato, butter, fenugreek", 34900, false),
                new Item(mains, "Veg Biryani", "Dum-cooked basmati", 27900, true),
                new Item(mains, "Mutton Rogan Josh", "Kashmiri chilli gravy", 42900, false));
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            menu.createItem(ownerId, rid, new MenuItemRequest(item.category(), item.name(), item.description(), item.pricePaise(),
                    item.veg(), null, true, i));
        }
    }

    private void seedChef(UUID ownerId, UUID rid, String name, String displayName, String phone, UUID stationId) {
        UUID chefId = chefs.create(ownerId, rid, new CreateChefRequest(name, phone, password, displayName, null, null, null)).id();
        UUID userId = users.findByPhone(phone).orElseThrow().getId();
        ConsentResponse consent = consents.forChefUser(userId);
        consents.accept(userId, consent.version(), consent.textHash(), "127.0.0.1");
        stations.assignChef(ownerId, rid, stationId, chefId); // needs consent first
    }
}
