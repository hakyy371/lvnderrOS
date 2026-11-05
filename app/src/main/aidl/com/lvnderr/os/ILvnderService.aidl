package com.lvnderr.os;

interface ILvnderService {
    void destroy();
    String exec(String binary, String state);
}

