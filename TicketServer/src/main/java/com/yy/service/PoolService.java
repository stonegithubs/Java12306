//package com.whut.service;
//
//import com.whut.constant.UserOrderStatus;
//import com.whut.dao.UserOrderRepository;
//import com.whut.dao.entity.UserOrder;
//import com.whut.domain.Session;
//import com.whut.domain.TrainTickets;
//import com.whut.exception.NetworkException;
//import com.whut.service.core.QueryTrainTicketsService;
//import com.whut.service.core.SubmitOrderService;
//import com.whut.service.core.TrainOrderPoolService;
//import com.whut.service.util.SendMsgService;
//import com.whut.service.util.SessionPoolService;
//import com.whut.service.util.StationService;
//import com.whut.util.SleepUtil;
//import org.apache.log4j.Logger;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.EnableAsync;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import java.io.Serializable;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicInteger;
//
///**
// * 该类专门负责查询12306的车票信息
// */
//@Service
//@EnableScheduling
//@EnableAsync // 开启多线程
//public class PoolService {
//
//    private static final Logger LOGGER = Logger.getLogger(PoolService.class);
//    private final int refresh_internal = 5000;
//    //包含4个优先级
//    private final int[] SLEEPS = new int[]{5000, 4000, 3000, 2000, 1000};
//    //date+fromStation+toStation : 一个查票任务（包含多个订单）
//    private Map<String, QueryHandler> handlerMap = new ConcurrentHashMap<>();
//    private ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(10);
//    private ThreadPoolExecutor threads = new ThreadPoolExecutor(10,
//        20, 20, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20));
//    private boolean inBreakTime;
//
//    @Autowired
//    private StationService stationService;
//    @Autowired
//    private UserOrderRepository userOrderRepository;
//    @Autowired
//    private QueryTrainTicketsService queryTrainTicketsService;
//    @Autowired
//    private SessionPoolService sessionPoolService;
//    @Autowired
//    private SubmitOrderService submitOrderService;
//    @Autowired
//    private SendMsgService sendMsgService;
//    @Autowired
//    private TrainOrderPoolService trainOrderPoolService;
//
//    @Scheduled(cron = "0 55 5 * * *") //每天5早上5点55，开始抢票
//    private void wakeup() {
//        inBreakTime = false;
//        loadOrder();
//    }
//
//    @Scheduled(cron = "0 30 23 * * *") //每天5早上5点55，开始抢票
//    private void sleep() {
//        inBreakTime = true;
//    }
//
//    //从数据库中加载未完成的订单，然后去处理订单
//    //启动项目时执行 以及 每天早上5:58执行，因为6点开售，所以可以提早一点
//    @PostConstruct
//    private void loadOrder() {
//        List<UserOrder> orders = userOrderRepository.findAll();
//        for (UserOrder order : orders) {
//            if (order.getStatus().equals(UserOrderStatus.RUSHING.getStatus())) {
//                addOrder(order);
//            }
//        }
//        LOGGER.info("从数据库中加载未完成的订单，然后去处理订单");
//    }
//
//    //同步当前查询次数
//    @Scheduled(fixedDelay = refresh_internal)
//    private void refreshQueryCount() {
//        if (inBreakTime) {
//            return;
//        }
//        Collection<QueryHandler> collection = handlerMap.values();
//        int orderCount = 0;
//        for (QueryHandler queryHandler : collection) {
//            //遍历所有的订单
//            for (InnerOrder innerOrder : queryHandler.queue) {
//                updateQueryCount(innerOrder);
//            }
//            for (InnerOrder innerOrder : queryHandler.blackQueue) {
//                updateQueryCount(innerOrder);
//            }
//            for (InnerOrder innerOrder : queryHandler.tempQueue) {
//                updateQueryCount(innerOrder);
//            }
//            orderCount += (queryHandler.queue.size() + queryHandler.tempQueue.size() + queryHandler.blackQueue.size());
//        }
//        //清空查询任务的查询次数
//        for (QueryHandler queryHandler : collection) {
//            queryHandler.count.set(0);
//        }
//        LOGGER.info(String.format("保存抢票次数到数据库，当前订单数量【%d】", orderCount));
//    }
//
//    //对于一个订单，其新增查票次数等于所有查询任务新增查询次数之和
//    private void updateQueryCount(InnerOrder innerOrder) {
//        String name;
//        int count = 0;
//        for (String date : innerOrder.dates) {
//            name = genQueryID(date, innerOrder.order.getFromStation(), innerOrder.order.getToStation(), innerOrder.order.getPriority());
//            if (handlerMap.containsKey(name)) {
//                count += handlerMap.get(name).count.get();
//            }
//        }
//        if (count > 0) {
//            innerOrder.order.setQueryCount(count + innerOrder.order.getQueryCount());
//            userOrderRepository.updateQueryCountByOrderId(innerOrder.order.getOrderId(), innerOrder.order.getQueryCount());
//        }
//    }
//
//    private String genQueryID(String date, String fromStation, String toStation, int priority) {
//        return String.format("%s-%s-%s-%d", date, fromStation, toStation, priority);
//    }
//
//    //定期检查订单是否过期
//    private boolean isExpired(InnerOrder innerOrder) {
//        long currTime = System.currentTimeMillis();
//        return innerOrder.order.getExpireTime().getTime() <= currTime;
//    }
//
//    private void removeOrderFromQueue(UserOrder order) {
//        String[] dates = order.getDates().split("/");
//        String name;
//        for (String date : dates) {
//            name = genQueryID(date,order.getFromStation(), order.getToStation(), order.getPriority());
//            if (handlerMap.containsKey(name)) {
//                QueryHandler handler = handlerMap.get(name);
//                for (InnerOrder innerOrder : handler.queue) {
//                    if (innerOrder.order.getOrderId().equals(order.getOrderId())) {
//                        handler.queue.remove(innerOrder);
//                    }
//                }
//                for (InnerOrder innerOrder : handler.tempQueue) {
//                    if (innerOrder.order.getOrderId().equals(order.getOrderId())) {
//                        handler.tempQueue.remove(innerOrder);
//                    }
//                }
//                for (InnerOrder innerOrder : handler.blackQueue) {
//                    if (innerOrder.order.getOrderId().equals(order.getOrderId())) {
//                        handler.blackQueue.remove(innerOrder);
//                    }
//                }
//            }
//        }
//    }
//
//    public void addOrder(UserOrder order) {
//        //为用户创建两个订单，一个用于候补抢票，一个用于刷票抢票
//        InnerOrder innerOrder = new InnerOrder(order);
//        String name;
//        for (String date : innerOrder.dates) {
//            name = genQueryID(date, order.getFromStation(), order.getToStation(), order.getPriority());
//            QueryHandler handler;
//            if (!handlerMap.containsKey(name)) {
//                handler = new QueryHandler(name, date, order.getFromStation(), order.getToStation(), order.getPriority());
//                handlerMap.put(name, handler);
//                handler.queue.add(innerOrder);
//                threads.execute(handler);
//            } else {
//                handler = handlerMap.get(name);
//                handler.queue.add(innerOrder);
//            }
//        }
//    }
//
//    public void cancelOrder(UserOrder order) {
//        removeOrderFromQueue(order);
//        order.setStatus(UserOrderStatus.CANCELED.getStatus());
//        userOrderRepository.save(order);
//    }
//
//    private static class InnerOrder {
//        UserOrder order;
//        Set<String> trainCodes = new HashSet<>();
//        Set<String> dates = new HashSet<>();
//        Set<String> seats = new HashSet<>();
//        List<String> people = new ArrayList<>();
//
//        InnerOrder(UserOrder userOrder) {
//            Collections.addAll(trainCodes, userOrder.getTrains().split("/"));
//            Collections.addAll(seats, userOrder.getSeats().split("/"));
//            Collections.addAll(dates, userOrder.getDates().split("/"));
//            Collections.addAll(people, userOrder.getPeople().split("/"));
//            this.order = userOrder;
//        }
//    }
//
//    //查的时候是根据 起始站/终点站/日期 来查询出所有的车次及其余票
//    //所以，相同 起始站/终点站/日期 的用户订单可以放在一起进行查询
//    //最后，根据用户的优先级选择优先级高的先下单。
//    private class QueryHandler implements Runnable, Serializable {
//        //该处理线程的name
//        String handlerName;
//        //查询条件
//        String date;
//        String fromStationCode;
//        String toStationCode;
//        int priority;
//        //用来查询的session
//        Session session = sessionPoolService.getSession(null);
//        //查询次数统计
//        AtomicInteger count;
//        //满足该查询条件的所有的订单
//        //根据用户要求的 起始站/终点站/日期/车次/座位类型 查询余票
//        Queue<InnerOrder> queue = new LinkedList<>();
//        Queue<InnerOrder> tempQueue = new LinkedList<>();
//        Queue<InnerOrder> blackQueue = new LinkedList<>();
//
//        QueryHandler(String handlerName, String date, String fromStation, String toStation, int priority) {
//            this.handlerName = handlerName;
//            this.date = date;
//            this.fromStationCode = stationService.getCodeByName(fromStation);
//            this.toStationCode = stationService.getCodeByName(toStation);
//            this.priority = priority;
//            this.count = new AtomicInteger(0);
//        }
//
//        /**
//         * 下单失败的订单关进小黑屋
//         * 1分钟后放出来
//         */
//        void addBlackRoom(InnerOrder innerOrder) {
//            blackQueue.add(innerOrder);
//            scheduledExecutorService.schedule(() -> {
//                if (!blackQueue.isEmpty()) {
//                    InnerOrder innerOrder1 = blackQueue.poll();
//                    queue.add(innerOrder1);
//                }
//            }, 1, TimeUnit.MINUTES);
//        }
//
//        @Override
//        public void run() {
//            //只要还有订单需要查询余票，该线程就没有结束
//            while ((!queue.isEmpty() || !blackQueue.isEmpty()) && !inBreakTime) {
//                int c = count.addAndGet(1);
//                LOGGER.info(String.format("【%s】的第【%d】次查询，队列中订单数量:【%d】", handlerName, c, queue.size() + blackQueue.size()));
//                //查询一次余票
//                List<TrainTickets> trainTicketsList;
//                try {
//                    trainTicketsList = queryTrainTicketsService.getTrainTickets(session, date, fromStationCode, toStationCode);
//                } catch (NetworkException e) {
//                    LOGGER.error(e.getMessage());
//                    continue;
//                }
//                //对队列中的所有订单按优先级次序进行一次匹配
//                while (!queue.isEmpty()) {
//                    //拿出一个订单进行处理
//                    InnerOrder innerOrder = queue.poll();
//                    if (isExpired(innerOrder)) {
//                        LOGGER.info(String.format("订单【%s】已过期", innerOrder.order.getOrderId()));
//                        innerOrder.order.setStatus(UserOrderStatus.EXPIRED.getStatus());
//                        userOrderRepository.save(innerOrder.order);
//                        continue;
//                    }
//                    if (!innerOrder.order.getStatus().equals(UserOrderStatus.RUSHING.getStatus())) {
//                        LOGGER.info(String.format("订单【%s】已结束", innerOrder.order.getOrderId()));
//                        continue;
//                    }
//                    //匹配所有车次，看看是否有足够的座位去下单
//                    MatchResult matchResult = matchOrder(trainTicketsList, innerOrder);
//                    //如果没有匹配到
//                    if (!matchResult.success) {
//                        //将该订单存放到临时队列中
//                        tempQueue.add(innerOrder);
//                    }
//                    //如果匹配到了
//                    else {
//                        //如果是普通下单
//                        if (!matchResult.isBackup) {
//                            List<String> seatTypes = new ArrayList<>(matchResult.foundSeats);
//                            String sequenceNo = submitOrderService.submit(
//                                innerOrder.order.getOpenId(),
//                                matchResult.trainTickets.getSecretStr(),
//                                date, innerOrder.order.getFromStation(),
//                                innerOrder.order.getToStation(),
//                                innerOrder.people, seatTypes);
//                            //下单失败
//                            if (sequenceNo == null) {
//                                LOGGER.warn(String.format("订单【%s】下单失败，将其关进小黑屋！", innerOrder.order.getOrderId()));
//                                //把该订单加进小黑屋，5分钟后再放出来
//                                addBlackRoom(innerOrder);
//                            }
//                            //下单成功
//                            else {
//                                LOGGER.info(String.format("订单【%s】下单成功，订单号为:%s", innerOrder.order.getOrderId(), sequenceNo));
//                                innerOrder.order.setStatus(UserOrderStatus.SUCCESS.getStatus());
//                                userOrderRepository.save(innerOrder.order);
//                                sendMsgService.send(innerOrder.order, sequenceNo);
//                                //下单成功，将订单加入到火车票订单池中，用户监控用户是否支付
//                                trainOrderPoolService.add(innerOrder.order);
//                            }
//                        }
//                        //如果是候补下单
//                        else {
//                            tempQueue.add(innerOrder);
////                            boolean success = submitOrderService.submitByAfterNate(
////                                innerOrder.order.getOpenId(),
////                                matchResult.trainTickets.getSecretStr(),
////                                innerOrder.foundSeats,
////                                innerOrder.people);
////                            if (!success) {
////                                //下单失败
////                                LOGGER.warn(String.format("订单【%s】候补下单失败，将其关进小黑屋！", innerOrder.order.getOrderId()));
////                                //把该订单加进小黑屋，2分钟后再放出来
////                                addBlackRoom(innerOrder);
////                            } else {
////                                LOGGER.info(String.format("订单【%s】候补下单成功", innerOrder.order.getOrderId()));
//////                                innerOrder.order.setStatus(UserOrderStatus.SUCCESS.getStatus());
//////                                userOrderRepository.save(innerOrder.order);
////                                //通知用户去支付
////                                sendMsgService.send(innerOrder.order);
////                                //下单成功，将订单加入到火车票订单池中，用户监控用户是否支付
//////                                trainOrderPoolService.add(innerOrder.order);
////                            }
//                        }
//                    }
//                }
//                //将任务队列和临时队列交换
//                Queue<InnerOrder> temp = queue;
//                queue = tempQueue;
//                tempQueue = temp;
//                //防止抢票太快，IP被封
//                SleepUtil.sleepRandomTime(SLEEPS[priority + 1], SLEEPS[priority]);
//            }
//            //一旦该任务结束，就将其从map中移除
//            handlerMap.remove(handlerName);
//            LOGGER.info(String.format("【%s】查询任务结束！！！", handlerName));
//        }
//
//        private class MatchResult {
//            boolean success = false;
//            boolean isBackup = false;
//            TrainTickets trainTickets = null;
//            //需要找到一个车次，有people.size()个座位，这些座位最好是同一类型，但也可以不同
//            List<String> foundSeats = new ArrayList<>();
//        }
//
//        //订单的 起始站 终点站 出发日期已经是满足的了，
//        //这里需要匹配是否有订单需要的车次和坐席信息。
//        //如果找到，则返回该车次信息，同时foundSeats应该包含people.size()个座位
//        private MatchResult matchOrder(List<TrainTickets> trainTicketsList, InnerOrder innerOrder) {
//            MatchResult matchResult = new MatchResult();
//            //打乱匹配的顺序，防止因为某辆列车候补人数较多导致每次都下单失败
//            Collections.shuffle(trainTicketsList);
//            for (TrainTickets trainTickets : trainTicketsList) {
//                //检查是否是选择的车次，不是就直接过滤
//                if (!innerOrder.trainCodes.contains(trainTickets.getTrainCode())) {
//                    continue;
//                }
//                //用户不愿意坐多辆车，所以在匹配每趟车时，需要清空foundSeats
//                matchResult.foundSeats.clear();
//                //检查优美用同一种座位类型的车票
//                for (String seat : innerOrder.seats) {
//                    String count = trainTickets.getTicketCount(seat);
//                    //如果某一种座位有足够的票，则说明找到了
//                    if ((count.equals("有") || (count.matches("^[1-9]\\d*$")
//                        && Integer.parseInt(count) >= innerOrder.people.size()))) {
//                        LOGGER.info(String.format("【%s】座位类型有足够的票", seat));
//                        //防止添加了其它座位类型
//                        matchResult.foundSeats.clear();
//                        for (int i = 0; i < innerOrder.people.size(); ++i) {
//                            matchResult.foundSeats.add(seat);
//                        }
//                        matchResult.success = true;
//                        matchResult.trainTickets = trainTickets;
//                        return matchResult;
//                    }
//
//                }
//                //检查有没有不同类型座位的车票
//                for (String seat : innerOrder.seats) {
//                    String count = trainTickets.getTicketCount(seat);
//                    //如果某种座位有票但不足够
//                    if (count.matches("^[1-9]\\d*$")
//                        && Integer.parseInt(count) < innerOrder.people.size()) {
//                        LOGGER.info(String.format("【%s】座位类型有【%s】张票，已添加进座位列表", seat, count));
//                        for (int i = 0; i < Integer.parseInt(count); ++i) {
//                            if (matchResult.foundSeats.size() == innerOrder.people.size()) {
//                                LOGGER.info("多种类型的座位混合，有了足够的票");
//                                matchResult.success = true;
//                                matchResult.trainTickets = trainTickets;
//                                return matchResult;
//                            }
//                            matchResult.foundSeats.add(seat);
//                        }
//                    }
//                }
//                //检查有没有候补车票
//                for (String seat : innerOrder.seats) {
//                    String count = trainTickets.getTicketCount(seat);
//                    //如果是候补订单，且该车次到座位类型支持候补抢票
//                    if (count.equals("无") && trainTickets.isCanBackup()) {
//                        LOGGER.info(String.format("【%s】车次的【%s】座位类型支持候补抢票", trainTickets.getTrainCode(), seat));
//                        //防止添加了其它座位类型
//                        matchResult.foundSeats.clear();
//                        for (int i = 0; i < innerOrder.people.size(); ++i) {
//                            matchResult.foundSeats.add(seat);
//                        }
//                        matchResult.success = true;
//                        matchResult.isBackup = true;
//                        matchResult.trainTickets = trainTickets;
//                        return matchResult;
//                    }
//                }
//            }
//            //所有车次都不能找到足够的座位，则匹配失败
//            return matchResult;
//        }
//    }
//
//
//
//    private abstract static class Handler {
//        //下一个处理者
//        private Handler nextHandler;
//        //设置下一个处理者
//        public void setNextHandler(Handler nextHandler){
//            this.nextHandler = nextHandler;
//        }
//        public final void giveNext(Object o){
//            if(nextHandler != null){
//                nextHandler.handle(o);
//            }
//        }
//        //处理订单的方法
//        protected abstract void handle(Object o);
//    }
//
//    private static class CommonOrderHandler extends Handler {
//
//
//        @Override
//        protected void handle(Object o) {
//
//        }
//    }
//
//    private static class AlternateOrderHandler extends Handler {
//
//        @Override
//        protected void handle(Object o) {
//
//        }
//    }
//
//
//
//
//
//}
