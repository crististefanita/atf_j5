package ro.rentzlab.app.ui.table;

import org.junit.Rule;
import org.junit.Test;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSizing;
import ro.cardtable.ui.table.TableViewportPolicy;
import ro.rentzlab.app.testing.AndroidComponentLayer;
import ro.rentzlab.app.testing.UiAuditRule;

/** Evaluated adoption comparisons, kept separate from the legacy geometry assertions. */
@AndroidComponentLayer
public final class RentzSharedPresentationTest {
    @Rule public final UiAuditRule audit = new UiAuditRule();

    @Test public void scoreActionsUseTheSharedReadableRolesAndOneAccessibleScale() {
        for (float scale : new float[]{1f, 1.5f, 2f}) {
            audit.equal("Shared primary result action at scale " + scale, 24f * scale,
                    ro.cardtable.ui.score.ScoreTypography.ACTION_PRIMARY.sp() * scale);
            audit.equal("Shared secondary result action at scale " + scale, 21f * scale,
                    ro.cardtable.ui.score.ScoreTypography.ACTION_SECONDARY.sp() * scale);
        }
        audit.equal("Dense numeric cells retain their size", 13, ro.cardtable.ui.score.ScoreTypography.VALUE.sp());
        audit.equal("Generic quiet dialogs retain their size", 14, ro.cardtable.ui.score.ScoreTypography.QUIET.sp());
    }

    @Test public void everyTrickUsesTheUnchangedSharedFaceAndTheMeasuredCenter() {
        for (int viewportHeight : new int[]{520, 599, 740, 896, 1140}) {
            for (float font : new float[]{1f, 1.5f, 2f}) {
                var viewport = new TableViewportPolicy(viewportHeight, font);
                var reference = PlayedCardMetrics.forTable(3, 360, font, viewport);
                for (int players = 3; players <= 6; players++) {
                    var profile = RentzCardSpace.profile(players, 360, font, viewport);
                    String context = players + "/" + viewportHeight + "/" + font;
                    audit.equal("Constant shared face width " + context, reference.cardWidthDp(), profile.playedCards().cardWidthDp());
                    audit.equal("Constant shared face height " + context, reference.cardHeightDp(), profile.playedCards().cardHeightDp());
                    audit.equal("Shared card slots " + context, PlayedCardMetrics.forTable(players, 360, font, viewport), profile.playedCards());
                    int seatWidth = TableSizing.seatWidthDp(360, viewport.isCompact());
                    var spec = new TableGeometry.BoardSpec(players, seatWidth, 66, profile.center(),
                            viewport.regionGapDp(), 0, profile.handClearance(), true);
                    var shared = spec.plan(360, 640, 0);
                    var actual = RentzCardSpace.plan(360, 640, players, seatWidth, 66,
                            viewport.regionGapDp(), profile.center().reservedHeight(), profile.handClearance());
                    audit.equal("Shared seat anchors " + context, shared.seats(), actual.seats());
                    audit.equal("Shared trick center " + context, shared.center(), actual.center());
                    audit.equal("Trick uses the reserved center " + context, actual.center().top(), actual.contentTop(profile.center().reservedHeight(), false));
                    audit.equal("Only sequence uses the free band " + context, actual.band().centeredTop(160), actual.contentTop(160, true));
                    audit.equal("Shared hand clearance " + context, 612, actual.seats().get(0).bottom());
                }
            }
        }
    }
}
