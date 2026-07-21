package com.SpringBootMVC.ExpensesTracker.controller;

import com.SpringBootMVC.ExpensesTracker.DTO.ExpenseDTO;
import com.SpringBootMVC.ExpensesTracker.DTO.FilterDTO;
import com.SpringBootMVC.ExpensesTracker.entity.Client;
import com.SpringBootMVC.ExpensesTracker.entity.Expense;
import com.SpringBootMVC.ExpensesTracker.service.CategoryService;
import com.SpringBootMVC.ExpensesTracker.service.ExpenseService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MainController {
  ExpenseService expenseService;
  CategoryService categoryService;

  @Autowired
  public MainController(ExpenseService expenseService, CategoryService categoryService) {
    this.expenseService = expenseService;
    this.categoryService = categoryService;
  }

  @GetMapping("/")
  public String landingPage(HttpSession session, Model model) {
    Client client = (Client) session.getAttribute("client");
    model.addAttribute("sessionClient", client);
    return "landing-page";
  }

  @GetMapping("/showAdd")
  public String addExpense(Model model) {
    model.addAttribute("expense", new ExpenseDTO());
    return "add-expense";
  }

  @PostMapping("/submitAdd")
  public String submitAdd(@ModelAttribute("expense") ExpenseDTO expenseDTO, HttpSession session) {
    Client client = (Client) session.getAttribute("client");
    expenseDTO.setClientId(client.getId());
    expenseService.save(expenseDTO);
    return "redirect:/list";
  }

  @GetMapping("/list")
  public String list(Model model, HttpSession session) {
    Client client = (Client) session.getAttribute("client");
    if (client == null) {
      return "redirect:/login"; // Guard against unauthenticated access
    }

    int clientId = client.getId();
    List<Expense> expenseList = expenseService.findAllExpensesByClientId(clientId);

    for (Expense expense : expenseList) {
      // Safely resolve Category
      if (expense.getCategory() != null) {
        expense.setCategoryName(
            categoryService.findCategoryById(expense.getCategory().getId()).getName());
      } else {
        expense.setCategoryName("Uncategorized");
      }

      // Safely format Date and Time
      if (expense.getDateTime() != null && !expense.getDateTime().isBlank()) {
        LocalDateTime ldt =
            LocalDateTime.parse(expense.getDateTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        expense.setDate(ldt.toLocalDate().toString());
        expense.setTime(ldt.toLocalTime().toString());
      }
    }

    model.addAttribute("expenseList", expenseList);
    model.addAttribute("filter", new FilterDTO());
    return "list-page";
  }

  @GetMapping("/showUpdate")
  public String showUpdate(@RequestParam("expId") int id, Model model) {
    Expense expense = expenseService.findExpenseById(id);
    ExpenseDTO expenseDTO = new ExpenseDTO();
    expenseDTO.setAmount(expense.getAmount());
    
    // Add safe null check for Category
    if (expense.getCategory() != null) {
      expenseDTO.setCategory(expense.getCategory().getName());
    } else {
      expenseDTO.setCategory("Uncategorized");
    }
    
    expenseDTO.setDescription(expense.getDescription());
    expenseDTO.setDateTime(expense.getDateTime());

    model.addAttribute("expense", expenseDTO);
    model.addAttribute("expenseId", id);
    return "update-page";
  }

  @PostMapping("/submitUpdate")
  public String update(
      @RequestParam("expId") int id,
      @ModelAttribute("expense") ExpenseDTO expenseDTO,
      HttpSession session) {
    Client client = (Client) session.getAttribute("client");
    expenseDTO.setExpenseId(id);
    expenseDTO.setClientId(client.getId());
    expenseService.update(expenseDTO);
    return "redirect:/list";
  }

  @GetMapping("/delete")
  public String delete(@RequestParam("expId") int id) {
    expenseService.deleteExpenseById(id);
    return "redirect:/list";
  }

  @PostMapping("/processFilter")
  public String processFilter(@ModelAttribute("filter") FilterDTO filter, Model model) {
    List<Expense> expenseList = expenseService.findFilterResult(filter);

    for (Expense expense : expenseList) {
      // Safely resolve Category
      if (expense.getCategory() != null) {
        expense.setCategoryName(
            categoryService.findCategoryById(expense.getCategory().getId()).getName());
      } else {
        expense.setCategoryName("Uncategorized");
      }

      // Safely format Date and Time
      if (expense.getDateTime() != null && !expense.getDateTime().isBlank()) {
        LocalDateTime ldt =
            LocalDateTime.parse(expense.getDateTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        expense.setDate(ldt.toLocalDate().toString());
        expense.setTime(ldt.toLocalTime().toString());
      }
    }

    model.addAttribute("expenseList", expenseList);
    return "filter-result";
  }
}
