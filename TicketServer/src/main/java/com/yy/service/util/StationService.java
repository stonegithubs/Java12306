package com.yy.service.util;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service

public class StationService {

    private static final Logger LOGGER = Logger.getLogger(StationService.class);

    private static Map<String, String> stationMap = new HashMap<>();

    @Value("${12306.station_file_path}")
    private String path;
    @PostConstruct
    void init(){
        try {
            FileReader fileReader = new FileReader(path);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String data = bufferedReader.readLine();
            String[] strings = data.split("@");
            for (String s : strings)
            {
                String[] split = s.split("\\|");
                stationMap.put(split[1], split[2]);
                stationMap.put(split[2], split[1]);
            }
        } catch (IOException e) {
            LOGGER.error("init stationMap failed");
            LOGGER.error(e.getMessage());
        }
    }

    public String getCodeByName(String name){
        return stationMap.get(name);
    }

    public String getNameByCode(String code){
        return stationMap.get(code);
    }
}
