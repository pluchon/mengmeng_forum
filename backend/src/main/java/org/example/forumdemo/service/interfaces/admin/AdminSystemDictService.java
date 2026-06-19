package org.example.forumdemo.service.interfaces.admin;

import java.util.List;
import java.util.Map;

public interface AdminSystemDictService {

    Map<String, List<Map<String, String>>> getDictData();
}
