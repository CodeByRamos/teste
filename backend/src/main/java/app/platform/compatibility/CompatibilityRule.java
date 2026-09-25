package app.platform.compatibility;

import java.util.List;

/**
 * One deterministic check. A rule returns no findings when the parts it needs are not in the build.
 */
public interface CompatibilityRule {

    List<CompatibilityFinding> evaluate(BuildParts parts);
}
