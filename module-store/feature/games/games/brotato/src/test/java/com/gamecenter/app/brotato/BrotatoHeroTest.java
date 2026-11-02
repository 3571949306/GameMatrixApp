package com.gamecenter.app.brotato;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.brotato.engine.BrotatoArena;
import com.gamecenter.app.brotato.engine.BrotatoContent;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * 英雄职业系统单元测试：验证 6 位英雄的定义完整性、属性加成与边界安全性。
 */
public class BrotatoHeroTest {

    private BrotatoContent content;

    @Before
    public void setUp() throws IOException {
        Path root = findAssetsRoot();
        content = BrotatoContent.load(
                read(root.resolve("brotato/manifest.json")),
                read(root.resolve("brotato/enemies.json")),
                read(root.resolve("brotato/waves.json")));
    }

    private static Path findAssetsRoot() {
        Path[] candidates = new Path[] {
                Paths.get("src/main/assets"),
                Paths.get("module-store/feature/games/games/brotato/src/main/assets")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("brotato/manifest.json"))) return candidate;
        }
        throw new IllegalStateException("production brotato asset was not found for JVM test");
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void allSixHeroesAreDefinedWithUniqueIds() {
        BrotatoHero[] heroes = BrotatoHero.values();
        assertEquals("必须定义 6 位特色英雄", 6, heroes.length);

        Set<String> ids = new HashSet<>();
        for (BrotatoHero hero : heroes) {
            assertNotNull(hero.getId());
            assertTrue("英雄 ID 必须唯一", ids.add(hero.getId()));
            assertNotNull(hero.getNameCn());
            assertNotNull(hero.getTitle());
            assertNotNull(hero.getEmoji());
            assertNotNull(hero.getDescription());
        }
    }

    @Test
    public void fromIdLookupWorksSafely() {
        assertEquals(BrotatoHero.ALL_ROUNDER, BrotatoHero.fromId("all_rounder"));
        assertEquals(BrotatoHero.BRAWLER, BrotatoHero.fromId("brawler"));
        assertEquals(BrotatoHero.MAGE, BrotatoHero.fromId("mage"));
        assertEquals(BrotatoHero.CHUNKY, BrotatoHero.fromId("chunky"));
        assertEquals(BrotatoHero.LUCKY, BrotatoHero.fromId("lucky"));
        assertEquals(BrotatoHero.SPEEDSTER, BrotatoHero.fromId("speedster"));

        // 未知或 null 安全回退到 ALL_ROUNDER
        assertEquals(BrotatoHero.ALL_ROUNDER, BrotatoHero.fromId(null));
        assertEquals(BrotatoHero.ALL_ROUNDER, BrotatoHero.fromId("unknown_hero_id"));
    }

    @Test
    public void allRounderMaintainsBaseline() {
        BrotatoArena arena = new BrotatoArena(content, BrotatoArena.DIFFICULTY_NORMAL, 1001L);
        arena.start();
        BrotatoHero.ALL_ROUNDER.applyTo(arena);

        assertEquals(BrotatoArena.BASE_MAX_HP, arena.maxHp());
        assertEquals(BrotatoArena.BASE_MAX_HP, arena.hp());
        assertEquals(BrotatoArena.BASE_MOVE_SPEED, arena.moveSpeed(), 0.001f);
        assertEquals(BrotatoArena.BASE_BULLET_DAMAGE, arena.bulletDamage(), 0.001f);
        assertEquals(BrotatoArena.BASE_SHOT_COUNT, arena.shotCount());
    }

    @Test
    public void brawlerHasExtraShotAndRapidFire() {
        BrotatoArena arena = new BrotatoArena(content, BrotatoArena.DIFFICULTY_NORMAL, 1002L);
        arena.start();
        BrotatoHero.BRAWLER.applyTo(arena);

        assertEquals(BrotatoArena.BASE_SHOT_COUNT + 1, arena.shotCount());
        assertTrue("狂暴斗士射击间隔更短（攻速更快）", arena.fireIntervalMs() < BrotatoArena.BASE_FIRE_INTERVAL_MS);
        assertEquals(8, arena.maxHp()); // 10 - 2
    }

    @Test
    public void chunkyHasMassiveHealth() {
        BrotatoArena arena = new BrotatoArena(content, BrotatoArena.DIFFICULTY_NORMAL, 1003L);
        arena.start();
        BrotatoHero.CHUNKY.applyTo(arena);

        assertEquals(22, arena.maxHp()); // 10 + 12
        assertEquals(22, arena.hp());
        assertTrue("重装巨兽伤害有所加成", arena.bulletDamage() > BrotatoArena.BASE_BULLET_DAMAGE);
        assertTrue("重装巨兽移速有所牺牲", arena.moveSpeed() < BrotatoArena.BASE_MOVE_SPEED);
    }

    @Test
    public void mageHasHighBurstDamage() {
        BrotatoArena arena = new BrotatoArena(content, BrotatoArena.DIFFICULTY_NORMAL, 1004L);
        arena.start();
        BrotatoHero.MAGE.applyTo(arena);

        assertTrue("奥术法师拥有高单发伤害", arena.bulletDamage() >= 1.6f);
    }

    @Test
    public void speedsterHasSuperMobility() {
        BrotatoArena arena = new BrotatoArena(content, BrotatoArena.DIFFICULTY_NORMAL, 1005L);
        arena.start();
        BrotatoHero.SPEEDSTER.applyTo(arena);

        assertTrue("疾风幽灵拥有超高移速", arena.moveSpeed() >= BrotatoArena.BASE_MOVE_SPEED * 1.35f);
    }
}
