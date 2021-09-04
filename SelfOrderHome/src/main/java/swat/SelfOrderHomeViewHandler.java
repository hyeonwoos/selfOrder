package swat;

import swat.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class SelfOrderHomeViewHandler {


    @Autowired
    private SelfOrderHomeRepository selfOrderHomeRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPayed_then_CREATE_1 (@Payload Payed payed) {
        try {
            if (payed.isMe()) {
            	// view 객체 생성
            	SelfOrderHome selfOrderHome = new SelfOrderHome();
                // view 객체에 이벤트의 Value 를 set 함
            	selfOrderHome.setOrderId(payed.getOrderId());
            	selfOrderHome.setUserId(payed.getUserId());
            	selfOrderHome.setMenuId(payed.getMenuId());
            	selfOrderHome.setPayId(payed.getId());
            	selfOrderHome.setQty(payed.getQty());
            	selfOrderHome.setStatus("Payed");
                // view 레파지 토리에 save
            	selfOrderHomeRepository.save(selfOrderHome);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    @StreamListener(KafkaProcessor.INPUT)
    public void whenAssigned_then_UPDATE_1(@Payload Assigned assigned) {
        try {
            if (assigned.isMe()) {
                // view 객체 조회
            	List<SelfOrderHome> selfOrderHomeList = selfOrderHomeRepository.findByOrderId(assigned.getOrderId());
                for(SelfOrderHome selfOrderHome : selfOrderHomeList){
                	selfOrderHome.setStoreId(assigned.getId());
                	selfOrderHome.setStatus("Assigned");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	selfOrderHomeRepository.save(selfOrderHome);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderCancelled_then_UPDATE_2(@Payload OrderCancelled orderCancelled) {
        try {
            if (orderCancelled.isMe()) {
                // view 객체 조회
            	List<SelfOrderHome> selfOrderHomeList = selfOrderHomeRepository.findByOrderId(orderCancelled.getId());
            	for(SelfOrderHome selfOrderHome : selfOrderHomeList){
                	selfOrderHome.setStoreId(orderCancelled.getId());
                	selfOrderHome.setStatus("Refunded");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	selfOrderHomeRepository.save(selfOrderHome);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenRefunded_then_DELETE_1(@Payload Refunded refunded) {
        try {
            if (refunded.isMe()) {
            	List<SelfOrderHome> selfOrderHomeList = selfOrderHomeRepository.findByOrderId(refunded.getOrderId());
                for(SelfOrderHome selfOrderHome : selfOrderHomeList){
                	selfOrderHome.setStoreId(refunded.getId());
                	selfOrderHome.setStatus("Refunded");
                    // view 객체에 이벤트의 eventDirectValue 를 set 함
                    // view 레파지 토리에 save
                	selfOrderHomeRepository.save(selfOrderHome);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}