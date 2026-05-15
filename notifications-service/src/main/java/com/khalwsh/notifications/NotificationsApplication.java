package com.khalwsh.notifications;

import com.khalwsh.notifications.service.EmailSenderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailSender;

@SpringBootApplication
public class NotificationsApplication {

//	@Autowired
//	private EmailSenderService mailSender;

	public static void main(String[] args) {
		SpringApplication.run(NotificationsApplication.class, args);
	}

//	@EventListener(ApplicationReadyEvent.class)
//	public void sendMail() {
//		mailSender.sendEmail("abdelrhmansersawy@gmail.com\n" ,
//				"khalwsh made you ICPC Finalist" ,
//				"notification services says Hi!");
//	}
}
