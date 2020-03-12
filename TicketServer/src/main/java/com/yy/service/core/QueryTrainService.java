package com.yy.service.core;

import com.alibaba.fastjson.JSONArray;
import com.yy.constant.SeatType;
import com.yy.constant.TicketField;
import com.yy.domain.Session;
import com.yy.domain.Train;
import com.yy.service.api.API12306Service;
import com.yy.service.util.SessionPoolService;
import com.yy.service.util.StationService;
import com.yy.util.TimeFormatUtil;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;

@Service
public class QueryTrainService {

    private static final Logger LOGGER = Logger.getLogger(QueryTrainService.class);

    @Autowired
    API12306Service api12306Service;
    @Autowired
    StationService stationService;

    public List<Train> getTrains(Session session, String date,String fromStation, String toStation) {

        //验证车站号是否正确
        String fromStationCode = stationService.getCodeByName(fromStation);
        String toStationCode = stationService.getCodeByName(toStation);
        if (fromStation == null || toStation == null) {
            LOGGER.error(String.format("gettrain: 找不到车站【%s】和【%s】", fromStation, toStation));
            return null;
        }

        JSONArray array = api12306Service.queryTickets(session, date, fromStationCode, toStationCode);
        if (array == null) {
            LOGGER.error("查询余票失败，检查IP是否被封？");
            return null;
        }
        List<Train> list = new ArrayList<>();

        for (int i = 0; i < array.size(); ++i) {
            String string = array.getString(i);
            String[] ticketInfo = string.split("\\|");
            if (!ticketInfo[TicketField.REMARK.getI()].equals("预订")) {
                continue;
            }
            Train train = new Train();
            train.setTrainCode(ticketInfo[TicketField.TRAIN_CODE.getI()]);
            train.setSecretStr(ticketInfo[TicketField.SECRET_STR.getI()]);
            train.setRemark(ticketInfo[TicketField.REMARK.getI()]);
            train.setCanBackup(ticketInfo[TicketField.CAN_BACKUP.getI()].equals("1"));
            train.setOther(ticketInfo[TicketField.OTHER.getI()]);
            train.setFromStation(fromStation);
            train.setToStation(toStation);

            train.setFromTime(ticketInfo[TicketField.FROM_TIME.getI()]);
            train.setToTime(ticketInfo[TicketField.TO_TIME.getI()]);
            train.setDuration(ticketInfo[TicketField.DURATION.getI()]);
            train.setFromDate(ticketInfo[TicketField.START_DATE.getI()]);
            long t1 = TimeFormatUtil.date2Stamp(train.getFromDate() + " " + train.getFromTime(), "yyyyMMdd HH:mm");
            long delta = Integer.parseInt(train.getDuration().split(":")[0]) * 3600000 + Integer.parseInt(train.getDuration().split(":")[1]) * 60000;
            long t2 = t1 + delta;
            train.setToDate(TimeFormatUtil.stamp2Date(t2, "yyyyMMdd"));
            //座位类型及其数量
            for (SeatType seatType : SeatType.values()) {
                train.setTicketCount(seatType.getName(), ticketInfo[seatType.getI()]);
            }

            //todo 查询票价
//            String train_no = ticketInfo[TicketField.TRAIN_NO.getI()];
//            String from_station_no = ticketInfo[TicketField.FROM_STATION_NO.getI()];
//            String to_station_no = ticketInfo[TicketField.TO_STATION_NO.getI()];
//            String seat_types = ticketInfo[TicketField.SEAT_TYPES.getI()];
//            JSONObject prices = api12306Service.queryTicketPrices(session, train_no, from_station_no, to_station_no, seat_types, date);
//            if (prices == null){
//                continue;
//            }
//            Map<String, String> map = new HashMap<>();
//            for (String key : prices.keySet()){
//                SeatType seatType = SeatType.find(key);
//                if (seatType != null){
//                    map.put(seatType.getName(), prices.getString(key));
//                }
//            }
//            train.setPrices(map);
//            train.setLowestPrice(prices.getString("WZ")+"起");

            list.add(train);
        }
        return list;
    }

    public long getExpireTime(Session session, String dates, String fromStation, String toStation, String trainCodes) {

        String[] dateArr = dates.split("/");
        Arrays.sort(dateArr, Comparator.reverseOrder());
        long expireTime = 0;
        List<Train> trains = getTrains(session, dateArr[0], fromStation, toStation);
        Set<String> set = new HashSet<>();
        Collections.addAll(set, trainCodes.split("/"));
        //从晚到早进行遍历
        for(int i = trains.size()-1; i >= 0; --i){
            if (set.contains(trains.get(i).getTrainCode()))
            {
                String timeStr = dateArr[0] + " " + trains.get(i).getFromTime();
                long timestamp = TimeFormatUtil.date2Stamp(timeStr, "yyyy-MM-dd HH:mm");
                expireTime = timestamp - 1000 * 3600 * 2;
                break;
            }
        }
        return expireTime;
    }
}
