package com.jobsdb.autoapply.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class JobsdbService {
    @Value("${webdriver.chrome.driver}")
    private String chromeDriverPath;

    @Value("${jobsdb.email}")
    private String email;

    @Value("${jobsdb.password}")
    private String password;

    @Value("${jobsdb.keyword}")
    private String keyword;

    private WebDriver driver;
    private WebDriverWait wait;

    // Predefined answers for common questions
    private final Map<String, String> answers = new HashMap<>();

    List<Map<String, String>> jobs = new ArrayList<>();

    @PostConstruct
    public void init() {
        System.setProperty("webdriver.chrome.driver", chromeDriverPath);
        ChromeOptions options = new ChromeOptions();

        // Disable automation flags
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // Add user-agent to mimic a real browser
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36");

        // Optional: Disable popup blocking and infobars
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");

//        options.addArguments("user-data-dir=C:\\Users\\fredho\\AppData\\Local\\Google\\Chrome\\User Data");
//        options.addArguments("profile-directory=Default");


        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        // Hide WebDriver property via JavaScript
        ((JavascriptExecutor) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

        // Populate answers (customize these based on your profile)
        answers.put("How many years' experience do you have as an analyst programmer?", "No experience");
        answers.put("How many years' experience do you have in an application support function?", "No experience");

    }

    @PreDestroy
    public void cleanup() {
        if (driver != null) {
            driver.quit();
        }
    }

    private void jsClick(WebElement element) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("arguments[0].scrollIntoView(true); arguments[0].click();", element);
    }

    public void login() {
        // Step 1: Navigate to JobsDB HK
        driver.get("https://hk.jobsdb.com/hk");

        // Step 2: Log in
        WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Sign in")));
        jsClick(loginButton);

        WebElement googleButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[descendant::div[contains(text(), 'Continue with Google')]]")));
        jsClick(googleButton);

        String originalWindow = driver.getWindowHandle();
        boolean switched = false;
        for (int i = 0; i < 15 && !switched; i++) {
            Set<String> windowHandles = driver.getWindowHandles();
            if (windowHandles.size() > 1) {
                for (String windowHandle : windowHandles) {
                    if (!windowHandle.equals(originalWindow)) {
                        driver.switchTo().window(windowHandle);
                        System.out.println("Switched to new window: " + driver.getCurrentUrl());
                        switched = true;
                        break;
                    }
                }
            } else {
                String currentUrl = driver.getCurrentUrl();
                assert currentUrl != null;
                if (currentUrl.contains("accounts.google.com")) {
                    System.out.println("Redirected in same window: " + currentUrl);
                    switched = true;
                }
            }
        }

        if (!switched) {
            System.out.println("Window handles after timeout: " + driver.getWindowHandles());
            System.out.println("Final URL after timeout: " + driver.getCurrentUrl());
            throw new RuntimeException("Failed to reach Google login page");
        }

        WebElement emailField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("identifierId")));
        emailField.sendKeys(email);
        WebElement emailNextButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("identifierNext")));
        jsClick(emailNextButton);

        WebElement passwordField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("Passwd")));
        passwordField.sendKeys(password);
        WebElement passwordNextButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("passwordNext")));
        jsClick(passwordNextButton);

        driver.switchTo().window(originalWindow);
    }

    public void getJobs() throws InterruptedException {

        searchJobs();

// Step 4: Extract job listings
        List<WebElement> jobListings = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector("article[data-automation='normalJob']")));

        for (WebElement job : jobListings) {
            Map<String, String> jobDetails = new HashMap<>();

// Extract job title from aria-label
            String jobTitle = "";
            try {
                jobTitle = job.getAttribute("aria-label");
            } catch (Exception e) {
                System.err.println("Job title not found for a job card: " + e.getMessage());
            }
            jobDetails.put("title", jobTitle);

// Extract the job link
            String jobLink = "";
            try {
                WebElement linkElement = job.findElement(By.cssSelector("a[href^='/job/']"));
                jobLink = linkElement.getAttribute("href");
            } catch (Exception e) {
                System.err.println("Job link not found for job: " + jobTitle + " - " + e.getMessage());
            }
            jobDetails.put("link", jobLink);

// Extract company name
            String company = "";
            try {
                WebElement companyElement = job.findElement(By.cssSelector("a[data-automation='jobCompany']"));
                company = companyElement.getText();
            } catch (Exception e) {
                System.err.println("Company name not found for job: " + jobTitle + " - " + e.getMessage());
            }
            jobDetails.put("company", company);
// Extract location (first span in the location/salary div)
            String location = "";
            try {
                WebElement locationElement = job.findElement(By.cssSelector("a[data-automation='jobLocation']"));
                location = locationElement.getText();
            } catch (Exception e) {
                System.err.println("Location not found for job: " + jobTitle + " - " + e.getMessage());
            }
            jobDetails.put("location", location);

            // Extract salary (if present, second span in the same div)
            try {
                WebElement salaryElement = job.findElement(By.cssSelector("div.eihuid5b.eihuidhf.eihuid6n > span:nth-child(2)"));
                String salary = salaryElement.getText();
                jobDetails.put("salary", salary);
            } catch (Exception e) {
                jobDetails.put("salary", "Not specified");
            }

// Extract posting date
            String postingDate = "";
            try {
                WebElement dateElement = job.findElement(By.cssSelector("span > div._109pqcno:first-child, span._18ybopc4"));
                postingDate = dateElement.getText();
                if (postingDate.contains("Viewed")) {
                    postingDate = postingDate.split("Viewed")[0].trim();
                }
            } catch (Exception e) {
                System.err.println("Posting date not found for job: " + jobTitle + " - " + e.getMessage());
            }
            jobDetails.put("date", postingDate);

            // Add to the list
            jobs.add(jobDetails);

            // Print for debugging
            System.out.println("Job: " + jobTitle + " | Company: " + company + " | Location: " + location + " | Salary: " + jobDetails.get("salary") + " | Date: " + postingDate + " | Link: " + jobLink);


        }

    }

    private void searchJobs() throws InterruptedException {
        // Step 3: Search for jobs
        WebElement searchBox = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("keywords-input")));
        searchBox.sendKeys(keyword);
        WebElement searchButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("searchButton")));
        searchButton.click();
    }

    public void applyToJobs() {
        try {
//             Step 5: Apply to each job
            for (Map<String, String> job : jobs) {
//                Map<String, String> jobDetails = new HashMap<>(f);
                try {
                    if (!job.get("company").equals("i-CABLE Communications Limited")) {
                        driver.get(job.get("link"));

                        System.out.println("Navigated to job page: " + job.get("title"));
                        Thread.sleep(2000);
                        WebElement applyButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("a[data-automation='job-detail-apply']")));
                        // Click the Apply button
                        applyButton.click();
                        System.out.println("Clicked 'Apply' for job: " + job.get("title"));
                        Thread.sleep(5000);
                        fillApplicationForm();

                        System.out.println("applied for job: " + job.get("title"));
                        // Handle post-apply actions (e.g., filling a form, if needed)
                        // This depends on the job page behavior (see notes below)
                        Thread.sleep(2000); // Wait briefly to observe the result (optional)

                        // Return to the listings page (optional, if you want to continue applying)
                        driver.navigate().back();
                    }
                } catch (Exception e) {
                    System.err.println("Failed to apply for job: " + job.get("title") + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error occurred: " + e.getMessage());
        } finally {
            driver.quit();
        }
    }

    private void fillApplicationForm() {

        try {
            // Step 1: Attempt to submit the form to trigger validation
            WebElement noCoverLetterRadio = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input[data-testid='coverLetter-method-none']")));
            jsClick(noCoverLetterRadio);
            System.out.println("Selected 'Don’t include a cover letter");

            WebElement continueButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button[data-testid='continue-button']")));
            continueButton.click();
        } catch (Exception e) {
            System.out.println("Initial continue button click failed, checking form..." + e.getMessage());
        }

        // Step 2: Check for unfilled required fields
        boolean formComplete = false;
        while (!formComplete) {
            try {
                // Look for error messages indicating unfilled fields
                List<WebElement> errorMessages = driver.findElements(By.cssSelector("[id*='question-'][id$='-message']"));
                if (!errorMessages.isEmpty()) {
                    formComplete = true; // No errors, form is complete
                    System.out.println("have question that can not answer with");
                    break;
                }

                // Step 3: Fill unfilled fields
//                for (WebElement error : errorMessages) {
//                    Thread.sleep(2000);
//                    String questionId = Objects.requireNonNull(error.getAttribute("id")).replace("-message", "");
//                    WebElement questionElement = driver.findElement(By.id(questionId));
//                    String questionText = driver.findElement(By.cssSelector("label[for='" + questionId + "']")).getText();
//
//                    // Handle dropdowns (<select>)
//                    if (questionElement.getTagName().equalsIgnoreCase("select")) {
//                        String answer = answers.getOrDefault(questionText, "2 years"); // Default answer
//                        Select select = new Select(questionElement);
//                        select.selectByVisibleText(answer);
//                        System.out.println("Filled: " + questionText + " with " + answer);
//                    }
//                    // Add handling for other input types (e.g., text) if needed
//                }

                // Step 4: Retry submitting the form
                WebElement continueButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("[data-testid='continue-button']")));
                Thread.sleep(2000);
                continueButton.click();
            } catch (Exception e) {
                System.err.println("Error filling form: " + e.getMessage());
                formComplete = true; // Exit loop to avoid infinite retry
            }
        }
        WebElement submitButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector("button[data-testid='review-submit-application']")));
        submitButton.click();

        System.out.println("Selected submitButton");
    }

}
