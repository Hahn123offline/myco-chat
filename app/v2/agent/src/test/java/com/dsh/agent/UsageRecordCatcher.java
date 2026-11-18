package com.dsh.agent;

import com.dsh.models.UsageRecord;
import com.dsh.models.UsageSink;

import java.util.ArrayList;
import java.util.List;

public final class UsageRecordCatcher implements UsageSink {
    public final List<UsageRecord> records = new ArrayList<>();
    @Override public void record(UsageRecord r) { records.add(r); }
}
