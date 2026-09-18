package com.antifraud.config;

import com.antifraud.entity.User;
import com.antifraud.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * 用户画像播种器：与 simulator 的 200 个用户（user_0001..user_0200）对齐，
 * 生成运营商「用户数据」静态画像入库（users 表）。
 */
@Component
public class UserSeeder implements CommandLineRunner {
    private final UserRepository userRepo;

    public UserSeeder(UserRepository userRepo) { this.userRepo = userRepo; }

    private static final String[] TERM_MODELS = {"iPhone 15","iPhone 14 Pro","华为 Mate 60","小米 14","OPPO Find X7","vivo X100","荣耀 Magic6","三星 S24"};
    private static final String[] CITIES = {"Beijing","Shanghai","Guangzhou","Shenzhen","Hangzhou","Chengdu"};
    private static final String[] CITY_IDS = {"010","021","020","0755","0571","028"};

    @Override public void run(String... args) {
        if (userRepo.count() > 0) return;

        Random rnd = new Random(42);  // 与 simulator 的欺诈用户分布一致
        for (int i = 1; i <= 200; i++) {
            User u = new User();
            String uid = String.format("user_%04d", i);
            u.setUserId(uid);
            u.setBillNo("139" + (10000000 + rnd.nextInt(90000000)));
            u.setCustState(2);                          // 在网
            u.setCustLvl(rnd.nextInt(5));                // 0无/1钻石/2金/3银/4普通
            u.setAge(18 + rnd.nextInt(53));              // 18-70
            u.setSex(rnd.nextInt(2));
            u.setOccupation(1 + rnd.nextInt(7));         // 1-7 行业
            u.setMarryState(rnd.nextInt(3));             // 0未知/1已婚/2未婚
            u.setRealNameFlag(rnd.nextBoolean() ? 1 : 11); // 实名
            int cityIdx = rnd.nextInt(CITIES.length);
            u.setCityId(CITY_IDS[cityIdx]);
            u.setCountyId(CITY_IDS[cityIdx] + (10 + rnd.nextInt(90)));
            u.setUserState(1);                           // 在用
            u.setUserCreditId(rnd.nextInt(15));          // 信用分档
            u.setUserCreditValue((long) (100 + rnd.nextInt(50000))); // 信用额度（分）
            u.setInnetDur(rnd.nextInt(3650));            // 在网时长（天）
            u.setTermMdl(TERM_MODELS[rnd.nextInt(TERM_MODELS.length)]);
            userRepo.save(u);
        }
        System.out.println("[UserSeeder] 已初始化 200 个用户画像");
    }
}
