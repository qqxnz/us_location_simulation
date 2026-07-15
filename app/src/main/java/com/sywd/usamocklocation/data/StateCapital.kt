package com.sywd.usamocklocation.data

data class StateCapital(
    val code: String,
    val stateNameEn: String,
    val stateNameZh: String,
    val capitalNameEn: String,
    val capitalNameZh: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun matches(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isEmpty()) return true
        return listOf(code, stateNameEn, stateNameZh, capitalNameEn, capitalNameZh)
            .any { it.contains(normalized, ignoreCase = true) }
    }
}

object StateCapitals {
    val all: List<StateCapital> = listOf(
        StateCapital("AL", "Alabama", "阿拉巴马州", "Montgomery", "蒙哥马利", 32.377716, -86.300568),
        StateCapital("AK", "Alaska", "阿拉斯加州", "Juneau", "朱诺", 58.301598, -134.420212),
        StateCapital("AZ", "Arizona", "亚利桑那州", "Phoenix", "菲尼克斯", 33.448143, -112.096962),
        StateCapital("AR", "Arkansas", "阿肯色州", "Little Rock", "小石城", 34.746613, -92.288986),
        StateCapital("CA", "California", "加利福尼亚州", "Sacramento", "萨克拉门托", 38.576668, -121.493629),
        StateCapital("CO", "Colorado", "科罗拉多州", "Denver", "丹佛", 39.739227, -104.984856),
        StateCapital("CT", "Connecticut", "康涅狄格州", "Hartford", "哈特福德", 41.764046, -72.682198),
        StateCapital("DE", "Delaware", "特拉华州", "Dover", "多佛", 39.157307, -75.519722),
        StateCapital("FL", "Florida", "佛罗里达州", "Tallahassee", "塔拉哈西", 30.438118, -84.281296),
        StateCapital("GA", "Georgia", "佐治亚州", "Atlanta", "亚特兰大", 33.749027, -84.388229),
        StateCapital("HI", "Hawaii", "夏威夷州", "Honolulu", "火奴鲁鲁", 21.307442, -157.857376),
        StateCapital("ID", "Idaho", "爱达荷州", "Boise", "博伊西", 43.617775, -116.199722),
        StateCapital("IL", "Illinois", "伊利诺伊州", "Springfield", "斯普林菲尔德", 39.798363, -89.654961),
        StateCapital("IN", "Indiana", "印第安纳州", "Indianapolis", "印第安纳波利斯", 39.768623, -86.162643),
        StateCapital("IA", "Iowa", "爱荷华州", "Des Moines", "得梅因", 41.591087, -93.603729),
        StateCapital("KS", "Kansas", "堪萨斯州", "Topeka", "托皮卡", 39.048191, -95.677956),
        StateCapital("KY", "Kentucky", "肯塔基州", "Frankfort", "法兰克福", 38.186722, -84.875374),
        StateCapital("LA", "Louisiana", "路易斯安那州", "Baton Rouge", "巴吞鲁日", 30.457069, -91.187393),
        StateCapital("ME", "Maine", "缅因州", "Augusta", "奥古斯塔", 44.307167, -69.781693),
        StateCapital("MD", "Maryland", "马里兰州", "Annapolis", "安纳波利斯", 38.978764, -76.490936),
        StateCapital("MA", "Massachusetts", "马萨诸塞州", "Boston", "波士顿", 42.358162, -71.063698),
        StateCapital("MI", "Michigan", "密歇根州", "Lansing", "兰辛", 42.733635, -84.555328),
        StateCapital("MN", "Minnesota", "明尼苏达州", "Saint Paul", "圣保罗", 44.955097, -93.102211),
        StateCapital("MS", "Mississippi", "密西西比州", "Jackson", "杰克逊", 32.303848, -90.182106),
        StateCapital("MO", "Missouri", "密苏里州", "Jefferson City", "杰斐逊城", 38.579201, -92.172935),
        StateCapital("MT", "Montana", "蒙大拿州", "Helena", "海伦娜", 46.585709, -112.018417),
        StateCapital("NE", "Nebraska", "内布拉斯加州", "Lincoln", "林肯", 40.808075, -96.699654),
        StateCapital("NV", "Nevada", "内华达州", "Carson City", "卡森城", 39.163914, -119.766121),
        StateCapital("NH", "New Hampshire", "新罕布什尔州", "Concord", "康科德", 43.206898, -71.537994),
        StateCapital("NJ", "New Jersey", "新泽西州", "Trenton", "特伦顿", 40.220596, -74.769913),
        StateCapital("NM", "New Mexico", "新墨西哥州", "Santa Fe", "圣菲", 35.682240, -105.939728),
        StateCapital("NY", "New York", "纽约州", "Albany", "奥尔巴尼", 42.652843, -73.757874),
        StateCapital("NC", "North Carolina", "北卡罗来纳州", "Raleigh", "罗利", 35.780430, -78.639099),
        StateCapital("ND", "North Dakota", "北达科他州", "Bismarck", "俾斯麦", 46.820850, -100.783318),
        StateCapital("OH", "Ohio", "俄亥俄州", "Columbus", "哥伦布", 39.961346, -82.999069),
        StateCapital("OK", "Oklahoma", "俄克拉何马州", "Oklahoma City", "俄克拉何马城", 35.492207, -97.503342),
        StateCapital("OR", "Oregon", "俄勒冈州", "Salem", "塞勒姆", 44.938461, -123.030403),
        StateCapital("PA", "Pennsylvania", "宾夕法尼亚州", "Harrisburg", "哈里斯堡", 40.264378, -76.883598),
        StateCapital("RI", "Rhode Island", "罗得岛州", "Providence", "普罗维登斯", 41.830914, -71.414963),
        StateCapital("SC", "South Carolina", "南卡罗来纳州", "Columbia", "哥伦比亚", 34.000343, -81.033211),
        StateCapital("SD", "South Dakota", "南达科他州", "Pierre", "皮尔", 44.367031, -100.346405),
        StateCapital("TN", "Tennessee", "田纳西州", "Nashville", "纳什维尔", 36.165810, -86.784241),
        StateCapital("TX", "Texas", "得克萨斯州", "Austin", "奥斯汀", 30.274670, -97.740349),
        StateCapital("UT", "Utah", "犹他州", "Salt Lake City", "盐湖城", 40.777477, -111.888237),
        StateCapital("VT", "Vermont", "佛蒙特州", "Montpelier", "蒙彼利埃", 44.262436, -72.580536),
        StateCapital("VA", "Virginia", "弗吉尼亚州", "Richmond", "里士满", 37.538857, -77.433640),
        StateCapital("WA", "Washington", "华盛顿州", "Olympia", "奥林匹亚", 47.035805, -122.905014),
        StateCapital("WV", "West Virginia", "西弗吉尼亚州", "Charleston", "查尔斯顿", 38.336246, -81.612328),
        StateCapital("WI", "Wisconsin", "威斯康星州", "Madison", "麦迪逊", 43.074684, -89.384445),
        StateCapital("WY", "Wyoming", "怀俄明州", "Cheyenne", "夏延", 41.140259, -104.820236),
        StateCapital("DC", "Washington, D.C.", "华盛顿特区", "Washington", "华盛顿", 38.889939, -77.009050),
    )

    fun find(code: String?): StateCapital? = all.firstOrNull { it.code == code }
}
