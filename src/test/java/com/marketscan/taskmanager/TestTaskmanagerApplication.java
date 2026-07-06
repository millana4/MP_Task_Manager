package com.marketscan.taskmanager;

import org.springframework.boot.SpringApplication;

public class TestTaskmanagerApplication {

	public static void main(String[] args) {
		SpringApplication.from(TaskmanagerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
