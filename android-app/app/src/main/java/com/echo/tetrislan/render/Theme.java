package com.echo.tetrislan.render;

import android.graphics.Color;

public class Theme {
    public String name;
    public int bg, board, boardStroke, blockFlash, btn, btnOn, btnPause, btnTop, text, textMuted, score, fxText;
    public int[] colors;
    public Theme(String n, int bg, int bo, int bs, int bf, int btn, int boN, int bp, int bt, int tx, int tm, int sc, int fx, int[] co) {
        this.name=n; this.bg=bg; this.board=bo; this.boardStroke=bs; this.blockFlash=bf;
        this.btn=btn; this.btnOn=boN; this.btnPause=bp; this.btnTop=bt;
        this.text=tx; this.textMuted=tm; this.score=sc; this.fxText=fx; this.colors=co;
    }
}
