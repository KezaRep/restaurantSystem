package model;

import java.util.*;
import java.util.stream.Collectors;

public final class Menu {

    public record Dish(String id, String name, String category,
                       int price, String icon, String description) {}

    /** Danh sách món — chia theo nhóm để UI dễ phân loại */
    public static final List<Dish> ITEMS = List.of(
        // ═══════ KHAI VỊ ═══════
        new Dish("A01", "Salad rau củ",       "Khai vị",   79000, "SALAD",  "Rau tươi, sốt dầu giấm"),
        new Dish("A02", "Súp bí đỏ",          "Khai vị",   69000, "SOUP",   "Bí đỏ nghiền và kem tươi"),
        new Dish("A03", "Gỏi cuốn tôm",        "Khai vị",   85000, "SALAD",  "Tôm tươi, bánh tráng, rau sống"),
        new Dish("A04", "Súp hải sản",         "Khai vị",   89000, "SOUP",   "Tôm, mực, nghêu nấu kem"),
        new Dish("A05", "Bruschetta cà chua",  "Khai vị",   65000, "SALAD",  "Bánh mì nướng, cà chua, basil"),
        new Dish("A06", "Khoai tây chiên",     "Khai vị",   55000, "SALAD",  "Khoai giòn, sốt mayonnaise"),

        // ═══════ MÓN CHÍNH ═══════
        new Dish("M01", "Bò bít tết",          "Món chính", 189000, "STEAK", "Bò áp chảo, sốt tiêu đen"),
        new Dish("M02", "Mì Ý sốt bò",         "Món chính", 129000, "PASTA", "Sốt cà chua và bò bằm"),
        new Dish("M03", "Cơm gà nướng",        "Món chính",  99000, "RICE",  "Gà ướp thảo mộc, cơm nóng"),
        new Dish("M04", "Pizza hải sản",       "Món chính", 169000, "PIZZA", "Tôm, mực và phô mai"),
        new Dish("M05", "Cá hồi áp chảo",      "Món chính", 209000, "STEAK", "Cá hồi Na Uy, sốt chanh bơ"),
        new Dish("M06", "Risotto nấm truffle", "Món chính", 179000, "RICE",  "Gạo Arborio, nấm, phô mai"),
        new Dish("M07", "Burger bò phô mai",   "Món chính", 139000, "STEAK", "Bò Úc, cheddar, khoai tây"),
        new Dish("M08", "Mì Ý carbonara",      "Món chính", 135000, "PASTA", "Trứng, pancetta, parmesan"),

        // ═══════ TRÁNG MIỆNG ═══════
        new Dish("D01", "Bánh tiramisu",       "Tráng miệng", 69000, "CAKE", "Kem mascarpone và cacao"),
        new Dish("D02", "Cheesecake việt quất","Tráng miệng", 75000, "CAKE", "Phô mai kem, sốt việt quất"),
        new Dish("D03", "Kem flan",            "Tráng miệng", 49000, "CAKE", "Bánh flan mềm, caramel"),
        new Dish("D04", "Panna cotta",         "Tráng miệng", 65000, "CAKE", "Kem Ý, sốt dâu tây"),

        // ═══════ ĐỒ UỐNG ═══════
        new Dish("B01", "Trà đào cam sả",      "Đồ uống",  49000, "TEA",    "Trà thanh mát, đào ngọt"),
        new Dish("B02", "Cà phê sữa đá",       "Đồ uống",  39000, "COFFEE", "Cà phê Việt Nam"),
        new Dish("B03", "Sinh tố xoài",        "Đồ uống",  55000, "TEA",    "Xoài chín, sữa chua"),
        new Dish("B04", "Nước ép cam",         "Đồ uống",  45000, "TEA",    "Cam tươi nguyên chất"),
        new Dish("B05", "Cappuccino",          "Đồ uống",  50000, "COFFEE", "Espresso, sữa bọt"),
        new Dish("B06", "Trà xanh matcha",     "Đồ uống",  52000, "TEA",    "Matcha Nhật, sữa tươi")
    );

    /** Thứ tự hiển thị các nhóm */
    public static final List<String> CATEGORY_ORDER = List.of(
        "Khai vị", "Món chính", "Tráng miệng", "Đồ uống"
    );

    private Menu() {}

    public static Dish get(String id) {
        return ITEMS.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    public static List<Dish> all() {
        return ITEMS;
    }

    /** Nhóm món theo category, giữ đúng thứ tự CATEGORY_ORDER */
    public static Map<String, List<Dish>> grouped() {
        Map<String, List<Dish>> map = new LinkedHashMap<>();
        for (String c : CATEGORY_ORDER) {
            List<Dish> list = ITEMS.stream()
                    .filter(d -> d.category().equals(c))
                    .collect(Collectors.toList());
            if (!list.isEmpty()) map.put(c, list);
        }
        return map;
    }

    /** Tìm kiếm theo tên / mô tả / id (không phân biệt hoa thường) */
    public static List<Dish> search(String keyword) {
        if (keyword == null || keyword.isBlank()) return ITEMS;
        String k = keyword.toLowerCase().trim();
        return ITEMS.stream()
                .filter(d -> d.name().toLowerCase().contains(k)
                        || d.description().toLowerCase().contains(k)
                        || d.id().toLowerCase().contains(k)
                        || d.category().toLowerCase().contains(k))
                .collect(Collectors.toList());
    }
}