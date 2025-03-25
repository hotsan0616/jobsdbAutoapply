package com.jobsdb.autoapply;

import com.jobsdb.autoapply.service.JobsdbService;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Duration;

@SpringBootApplication
public class AutoapplyApplication {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(AutoapplyApplication.class, args);
        JobsdbService jobsdbService = context.getBean(JobsdbService.class);
        jobsdbService.login();
        jobsdbService.getJobs();
        jobsdbService.applyToJobs();
    }


}
