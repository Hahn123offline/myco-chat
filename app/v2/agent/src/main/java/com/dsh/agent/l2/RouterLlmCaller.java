package com.dsh.agent.l2;

import com.dsh.agent.LlmCaller;
import com.dsh.egress.EgressTier;
import com.dsh.models.Capability;
import com.dsh.models.ModelRequest;
import com.dsh.models.ModelResponse;
import com.dsh.models.ModelRouter;
import com.dsh.models.Quality;
import com.dsh.models.SimpleTask;

import java.util.Collections;

/** 把 L2 会话接到 :models 路由（WHITE/GOOD/JSON）。 */
public final class RouterLlmCaller implements LlmCaller {

    private final ModelRouter router;

    public RouterLlmCaller(ModelRouter router) { this.router = router; }

    @Override public ModelResponse call(ModelRequest request) {
        SimpleTask task = new SimpleTask("l2.extract",
                Collections.singleton(Capability.JSON), Quality.GOOD, EgressTier.WHITE);
        return router.execute(task, request);
    }
}
