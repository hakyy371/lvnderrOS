
package com.lvnderr.os;

interface ILvnderService {
    void destroy() = 16777114;
    String exec(String binary, String state);
}
