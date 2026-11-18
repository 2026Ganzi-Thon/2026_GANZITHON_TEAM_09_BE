package com.haengunping.help.api;

import com.haengunping.help.application.HelpSignalsService.Signals;

/** {@code GET /help/signals} 응답(docs/api.md 8절). 두 키만 둔다. */
record HelpSignalsResponse(boolean lonely3Days, boolean checkAboveThreshold) {

    static HelpSignalsResponse from(Signals s) {
        return new HelpSignalsResponse(s.lonely3Days(), s.checkAboveThreshold());
    }
}
