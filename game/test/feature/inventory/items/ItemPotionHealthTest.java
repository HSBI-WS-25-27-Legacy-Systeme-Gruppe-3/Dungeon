package feature.inventory.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.Entity;
import engine.Game;
import engine.components.DrawComponent;
import feature.components.HealthComponent;
import feature.components.InventoryComponent;
import feature.health.DamageType;
import feature.systems.HealthSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests health-potion consumption through {@link ItemPotionHealth#use(Entity)}. */
public class ItemPotionHealthTest {

  private static final int MAX_HEALTH = 100;

  private Entity user;
  private HealthComponent health;
  private InventoryComponent inventory;
  private ItemPotionHealth potion;

  /** Creates a user with full health and three potions in one inventory slot. */
  @BeforeEach
  public void setup() {
    Game.removeAllEntities();
    user = new Entity();
    health = new HealthComponent(MAX_HEALTH);
    inventory = new InventoryComponent();
    potion = new ItemPotionHealth();
    potion.maxStackSize(3);
    potion.stackSize(3);
    assertTrue(inventory.add(potion));

    user.add(health);
    user.add(inventory);
    user.add(new DrawComponent(potion.inventoryAnimation()));
    Game.add(user);
  }

  /** Removes the test user from the game. */
  @AfterEach
  public void cleanup() {
    Game.removeAllEntities();
  }

  /** Full health preserves the potion stack and does not queue a healing effect. */
  @Test
  public void keepsPotionAtFullHealth() {
    potion.use(user);

    assertEquals(3, inventory.count(ItemPotionHealth.class));
    assertSame(potion, inventory.get(0).orElseThrow());
    assertEquals(0, health.calculateDamageOf(DamageType.HEAL));
    assertEquals(MAX_HEALTH, health.currentHealthpoints());
  }

  /** An injured user consumes one potion and receives its configured healing amount. */
  @Test
  public void healsAndConsumesOnePotion() {
    health.currentHealthpoints(60);

    potion.use(user);

    assertEquals(2, inventory.count(ItemPotionHealth.class));
    assertEquals(-potion.healAmount(), health.calculateDamageOf(DamageType.HEAL));
    new HealthSystem().execute();
    assertEquals(60 + potion.healAmount(), health.currentHealthpoints());
  }

  /** Consuming the final potion frees its inventory slot. */
  @Test
  public void removesLastPotionAfterHealing() {
    potion.stackSize(1);
    health.currentHealthpoints(60);

    potion.use(user);

    assertTrue(inventory.isEmpty());
    assertEquals(-potion.healAmount(), health.calculateDamageOf(DamageType.HEAL));
  }

  /** The existing health system limits a potion's healing to the user's maximum health. */
  @Test
  public void capsHealingAtMaximumHealth() {
    health.currentHealthpoints(MAX_HEALTH - 1);

    potion.use(user);
    new HealthSystem().execute();

    assertEquals(MAX_HEALTH, health.currentHealthpoints());
    assertEquals(2, inventory.count(ItemPotionHealth.class));
  }

  /** A user without a health component keeps the potion. */
  @Test
  public void keepsPotionWithoutHealthComponent() {
    user.remove(HealthComponent.class);

    potion.use(user);

    assertEquals(3, inventory.count(ItemPotionHealth.class));
  }

  /** Healing requires a potion that can actually be consumed from the user's inventory. */
  @Test
  public void doesNotHealWhenPotionIsMissing() {
    health.currentHealthpoints(60);
    inventory.remove(potion);

    potion.use(user);

    assertEquals(0, health.calculateDamageOf(DamageType.HEAL));
    assertEquals(60, health.currentHealthpoints());
  }

  /** A user without an inventory receives no healing and consumes no potion. */
  @Test
  public void doesNotHealWithoutInventoryComponent() {
    health.currentHealthpoints(60);
    user.remove(InventoryComponent.class);

    potion.use(user);

    assertEquals(0, health.calculateDamageOf(DamageType.HEAL));
    assertEquals(3, potion.stackSize());
  }
}
