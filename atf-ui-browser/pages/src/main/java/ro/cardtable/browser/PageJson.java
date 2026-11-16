package ro.cardtable.browser;

import java.util.LinkedHashMap;
import java.util.Map;
import ro.cardtable.browser.BrowserGame.*;
import ro.cardtable.browser.BrowserExperience.*;
import ro.cardtable.ui.animation.TrickCollectionMotion;
import ro.cardtable.ui.hand.HandLayoutMetrics;
import ro.cardtable.ui.table.PlayedCardMetrics;
import ro.cardtable.ui.table.TableGeometry;

/** Explicit public DTO projection keeps opponent hands and private journal data out of the page. */
final class PageJson {
    private PageJson() { }

    static Map<String, Object> fields(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int index = 0; index < pairs.length; index += 2) result.put((String) pairs[index], pairs[index + 1]);
        return result;
    }

    static String encode(Object value) {
        var output = new StringBuilder();
        append(output, value);
        return output.toString();
    }

    private static void append(StringBuilder output, Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof String text) {
            BrowserJson.quote(output, text);
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            String separator = "";
            for (var entry : map.entrySet()) {
                output.append(separator);
                BrowserJson.quote(output, (String) entry.getKey());
                output.append(':');
                append(output, entry.getValue());
                separator = ",";
            }
            output.append('}');
        } else if (value instanceof Iterable<?> list) {
            output.append('[');
            String separator = "";
            for (var item : list) {
                output.append(separator);
                append(output, item);
                separator = ",";
            }
            output.append(']');
        } else {
            append(output, project(value));
        }
    }

    private static Map<String, Object> project(Object value) {
        if (value instanceof TrickCollectionMotion.Sample sample)
            return fields("offset", sample.offset(), "travel", sample.travel(), "scale", sample.scale(), "alpha", sample.alpha());
        if (value instanceof BrowserSession.State state)
            return fields("revision", state.revision(), "view", state.view(), "profile", state.profile(),
                    "collections", state.collections(), "playback", state.playback());
        if (value instanceof View view)
            return fields("heading", view.heading(), "subtitle", view.subtitle(), "prompt", view.prompt(),
                    "seats", view.seats(), "hand", view.hand(), "played", view.played(), "center", view.center(),
                    "actions", view.actions(), "history", view.history(), "header", view.header(), "score", view.score(),
                    "scoreDetails", view.scoreDetails(), "rentzSequence", RentzPage.sequence());
        if (value instanceof Header header)
            return fields("mode", header.mode(), "title", header.title(), "progress", header.progress(), "bids", header.bids(),
                    "trump", header.trump(), "trumpDescription", header.trumpDescription(), "titleSize", header.titleSize(),
                    "progressSize", header.progressSize(), "bidSize", header.bidSize());
        if (value instanceof ScoreDetails details)
            return fields("liveTitle", details.liveTitle(), "summary", details.summary(), "pendingBid", details.pendingBid(),
                    "winner", details.winner(), "awards", details.awards(), "rules", details.rules(), "liveTable", details.liveTable());
        if (value instanceof Score score) return fields("title", score.title(), "table", score.table());
        if (value instanceof Field field) return fields("key", field.key(), "label", field.label(), "initial", field.initial(), "options", field.options());
        if (value instanceof Option option) return fields("value", option.value(), "label", option.label());
        if (value instanceof Token token) return fields("name", token.name(), "color", token.color());
        if (value instanceof Action action)
            return fields("key", action.key(), "label", action.label(), "emphasized", action.emphasized(),
                    "minimum", action.minimum(), "maximum", action.maximum());
        if (value instanceof Card card) return fields("rank", card.rank(), "suit", card.suit(), "label", card.label(), "action", card.action());
        if (value instanceof Seat seat)
            return fields("anchor", seat.anchor(), "name", seat.name(), "firstFact", seat.firstFact(),
                    "secondFact", seat.secondFact(), "active", seat.active());
        if (value instanceof BrowserHandAdapter.Feedback feedback)
            return fields("action", feedback.action(), "rejected", feedback.rejected(), "selected", feedback.selected(),
                    "revision", feedback.revision(), "suggestions", feedback.suggestions());
        if (value instanceof Played played)
            return fields("seat", played.seat(), "card", played.card(), "faceLabel", played.faceLabel(), "description", played.description());
        if (value instanceof PlayStep step)
            return fields("play", step.play(), "startsTrick", step.startsTrick(), "delayMillis", step.delayMillis(),
                    "collection", step.collection(), "bids", step.bids(), "seatFacts", step.seatFacts());
        if (value instanceof History history) return fields("headings", history.headings(), "rows", history.rows(), "grid", history.grid());
        if (value instanceof HistoryGrid grid)
            return fields("players", grid.players(), "rows", grid.rows(), "cardsLabel", grid.cardsLabel(),
                    "bidLabel", grid.bidLabel(), "pointsLabel", grid.pointsLabel(), "legend", grid.legend());
        if (value instanceof HistoryRow row) return fields("cards", row.cards(), "status", row.status(), "cells", row.cells());
        if (value instanceof HistoryCell cell)
            return fields("bid", cell.bid(), "total", cell.total(), "description", cell.description(), "missed", cell.missed(), "series", cell.series());
        if (value instanceof Rankings rankings) return fields("preferred", rankings.preferred(), "boards", rankings.boards());
        if (value instanceof RankBoard board) return fields("key", board.key(), "label", board.label(), "entries", board.entries());
        if (value instanceof RankEntry entry)
            return fields("label", entry.label(), "score", entry.score(), "ai", entry.ai(), "sample", entry.sample());
        if (value instanceof CollectionCue cue)
            return fields("winningSeat", cue.winningSeat(), "displayMillis", cue.displayMillis(), "travelMillis", cue.travelMillis(),
                    "cards", cue.cards(), "holdMillis", cue.holdMillis(), "seatFacts", cue.seatFacts());
        if (value instanceof BrowserGeometry.Layout layout)
            return fields("board", layout.board(), "hand", layout.hand(), "boardHeight", layout.boardHeight(),
                    "played", layout.played(), "bids", layout.bids(), "playedStyle", layout.playedStyle());
        if (value instanceof BidControls bids)
            return fields("columns", bids.columns(), "strip", bids.strip(), "choiceTarget", bids.choiceTarget(),
                    "rowHeight", bids.rowHeight(), "choiceInset", bids.choiceInset(), "submitWidth", bids.submitWidth(),
                    "submitTarget", bids.submitTarget(), "submitInsetX", bids.submitInsetX(), "submitInsetY", bids.submitInsetY(),
                    "textSize", bids.textSize());
        if (value instanceof PlayedCardMetrics.Layout played)
            return fields("cardWidthDp", played.cardWidthDp(), "cardHeightDp", played.cardHeightDp(),
                    "playerLabelHeightDp", played.playerLabelHeightDp(), "rowStrideDp", played.rowStrideDp(),
                    "centerHeightDp", played.centerHeightDp(), "slots", played.slots());
        if (value instanceof PlayedCardMetrics.Slot slot)
            return fields("seat", slot.seat(), "row", slot.row(), "leftDp", slot.leftDp());
        if (value instanceof TableGeometry.Plan plan) return fields("seats", plan.seats(), "center", plan.center());
        if (value instanceof TableGeometry.Rect rect)
            return fields("left", rect.left(), "top", rect.top(), "right", rect.right(), "bottom", rect.bottom());
        if (value instanceof HandLayoutMetrics.Fan fan)
            return fields("width", fan.width(), "first", fan.first(), "advance", fan.advance(), "cardWidth", fan.cardWidth());
        if (value instanceof BrowserExperience.Details details)
            return fields("name", details.name(), "savedName", details.savedName(), "catalogue", details.catalogue(), "notice", details.notice());
        throw new IllegalArgumentException("Unsupported public Rentz browser value: " + value.getClass().getName());
    }
}
