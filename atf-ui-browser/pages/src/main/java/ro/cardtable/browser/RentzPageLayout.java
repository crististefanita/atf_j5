package ro.cardtable.browser;

import java.util.Map;
import ro.cardtable.ui.table.TableGeometry;
import ro.cardtable.ui.table.TableViewportPolicy;

/** Retains the shared Whist seat plan and reuses its free-band calculation for Rentz sequence rows. */
final class RentzPageLayout {
    private RentzPageLayout() { }

    static BrowserGeometry.Layout measure(BrowserGame game, Map<String, String> parameters) {
        var common = BrowserGeometry.measure(game, parameters);
        if (!"true".equals(parameters.get("rentzSequence")) || common.board() == null) return common;
        int viewport = Integer.parseInt(parameters.get("viewport"));
        var policy = new TableViewportPolicy(viewport);
        var band = TableGeometry.fullWidthCardBand(common.board(), policy.regionGapDp());
        var previous = common.board().center();
        var center = band.height() < 4 ? previous
                : new TableGeometry.Rect(previous.left(), band.top(), previous.right(), band.bottom());
        return new BrowserGeometry.Layout(new TableGeometry.Plan(common.board().seats(), center), common.hand(),
                common.boardHeight(), null, common.bids(), Map.of());
    }
}
