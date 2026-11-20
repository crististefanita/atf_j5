package ro.rentzlab.app.ui.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import ro.cardtable.ui.cards.CardFaceMetrics;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableSizing;
import ro.cardtable.ui.table.TableViewportPolicy;

/** Reads the pinned Android AAR and the real native adapter; no copied browser formula. */
public final class RentzNativePresentationReference {
    private RentzNativePresentationReference() { }

    public static void main(String[] args) {
        var faces = new ArrayList<Map<String, Object>>();
        for (int height : new int[]{88, 70, 64}) {
            var face = CardFaceMetrics.atHeight(height);
            faces.add(Map.of("height", height, "width", face.widthDp(), "rank", face.rankSizeSp(),
                    "suit", face.indexSizeSp(), "invertedRank", CardFaceMetrics.INVERTED_RANK_SIZE_DP * face.scale(),
                    "invertedSuit", CardFaceMetrics.INVERTED_SIZE_DP * face.scale(),
                    "centerSuit", CardFaceMetrics.CENTER_SUIT_SIZE_DP * face.scale()));
        }
        var boards = new ArrayList<Map<String, Object>>();
        for (int players = 3; players <= 6; players++) for (int width : new int[]{280, 360, 414, 600}) {
            for (int viewportHeight : new int[]{569, 896}) {
                var viewport = new TableViewportPolicy(viewportHeight);
                var profile = RentzCardSpace.profile(players, width, 1f, viewport);
                int seatWidth = TableSizing.seatWidthDp(width, viewport.isCompact());
                int height = Math.max(440, Math.max(viewport.minimumBoardHeightDp(),
                        TableGeometry.minimumHeight(players, 66, profile.center().reservedHeight(), viewport.regionGapDp())));
                var board = RentzCardSpace.plan(width, height, players, seatWidth, 66, viewport.regionGapDp(),
                        profile.center().reservedHeight(), profile.handClearance());
                var cards = profile.playedCards();
                boards.add(Map.of("players", players, "width", width, "viewport", viewportHeight, "height", height,
                        "seats", board.seats().stream().map(RentzNativePresentationReference::rect).toList(),
                        "center", rect(board.center()), "cardWidth", cards.cardWidthDp(), "cardHeight", cards.cardHeightDp(),
                        "slots", cards.slots().stream().map(slot -> List.of(slot.seat(), slot.row(), slot.leftDp())).toList()));
            }
        }
        var scoreTypography = new java.util.LinkedHashMap<String, String>();
        for (var role : ro.cardtable.ui.score.ScoreTypography.values()) scoreTypography.put(role.cssName(), role.rem());
        System.out.println(new JSONObject(Map.of("faces", faces, "boards", boards, "scoreTypography", scoreTypography, "historyAppearance", ro.cardtable.ui.score.HistoryAppearance.REGULAR_GROUPS.cssTokens(), "menuPlans", menuPlans())));
    }

    private static List<Map<String, Object>> menuPlans() {
        var result = new ArrayList<Map<String, Object>>();
        for (boolean romanian : new boolean[]{false,true}) for (boolean settled : new boolean[]{false,true}) {
            var entries = ro.rentzlab.app.ui.chrome.RentzGameMenu.entries(new ro.rentzlab.app.ui.copy.RentzText(romanian), settled);
            result.add(Map.of("language", romanian ? "ro" : "en", "settled", settled, "entries",
                    entries.stream().map(e -> Map.of("id",e.id(),"label",e.label())).toList()));
        }
        return result;
    }

    private static List<Integer> rect(TableGeometry.Rect rect) {
        return List.of(rect.left(), rect.top(), rect.right(), rect.bottom());
    }
}
