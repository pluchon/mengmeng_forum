package org.pluchon.forum.common.utils;

import java.util.UUID;

//随机盐值生成，也就是生成随机扰动字符
public class UUIDUtils {
    public static String UUID32(){
        return UUID.randomUUID().toString().replace("-","");
    }
}
