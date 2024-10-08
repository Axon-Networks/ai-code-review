package com.googlesource.gerrit.plugins.aicodereview.mode.common.model.patch.diff;

import lombok.Data;

@Data
public class DiffContent {
    public String a;
    public String b;
    public String ab;
}
