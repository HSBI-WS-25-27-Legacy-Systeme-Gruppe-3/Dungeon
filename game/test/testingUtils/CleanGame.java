package testingUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Resets the global game state before and after every test of the annotated test class.
 *
 * <p>Before each test there are no entities, no systems (not even the essential systems that {@link
 * engine.game.ECSManagement} registers when it is loaded), no current level and no registered level
 * order in the {@link engine.level.loader.DungeonLoader}. The same reset runs again after each
 * test, even if the test failed. Tests therefore need no own cleanup code for this state.
 *
 * <p>Further global state can be reset by implementing a {@link TestStateReset}.
 *
 * @see CleanGameExtension
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ExtendWith(CleanGameExtension.class)
public @interface CleanGame {}
