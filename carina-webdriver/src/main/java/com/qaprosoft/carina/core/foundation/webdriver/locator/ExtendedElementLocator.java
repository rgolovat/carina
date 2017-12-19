/*
 * Copyright 2013-2015 QAPROSOFT (http://qaprosoft.com/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.qaprosoft.carina.core.foundation.webdriver.locator;

import java.lang.reflect.Field;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qaprosoft.alice.models.dto.RecognitionMetaType;
import com.qaprosoft.carina.core.foundation.webdriver.DriverPool;
import com.qaprosoft.carina.core.foundation.webdriver.ai.FindByAI;
import com.qaprosoft.carina.core.foundation.webdriver.ai.Label;
import com.qaprosoft.carina.core.foundation.webdriver.ai.impl.AliceRecognition;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.annotations.Predicate;

import io.appium.java_client.MobileBy;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;

/**
 * The default element locator, which will lazily locate an element or an element list on a page. This class is designed
 * for use with the {@link org.openqa.selenium.support.PageFactory} and understands the annotations
 * {@link org.openqa.selenium.support.FindBy} and {@link org.openqa.selenium.support.CacheLookup}.
 */
public class ExtendedElementLocator implements ElementLocator
{
	private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedElementLocator.class);
	
	private final SearchContext searchContext;
	private boolean shouldCache;
	private By by;
	private WebElement cachedElement;
	private List<WebElement> cachedElementList;

	private Boolean isPredicate;

	private String aiCaption;
	private Label aiLabel;
	
	/**
	 * Creates a new element locator.
	 * 
	 * @param searchContext
	 *            The context to use when finding the element
	 * @param field
	 *            The field on the Page Object that will hold the located value
	 */
	public ExtendedElementLocator(SearchContext searchContext, Field field)
	{
		this.searchContext = searchContext;

		if (field.isAnnotationPresent(FindBy.class))
		{
			LocalizedAnnotations annotations = new LocalizedAnnotations(field);
			this.shouldCache = annotations.isLookupCached();
			this.by = annotations.buildBy();
		}
		// Elements to be recognized by Alice
		if (field.isAnnotationPresent(FindByAI.class))
		{
			this.aiCaption = field.getAnnotation(FindByAI.class).caption();
			this.aiLabel = field.getAnnotation(FindByAI.class).label();
			// TODO: investigate if we need cache for AI elements
			this.shouldCache = true;
		}

		this.isPredicate = false;
		if (field.isAnnotationPresent(Predicate.class))
		{
			this.isPredicate = field.getAnnotation(Predicate.class).enabled();
		}
	}

	/**
	 * Find the element.
	 */
	@SuppressWarnings("rawtypes")
	public WebElement findElement()
	{
		if (cachedElement != null && shouldCache)
		{
			return cachedElement;
		}
		
		WebDriver driver = DriverPool.getDriver();
		WebElement element = null;
		
		if (!isPredicate)
		{
			NoSuchElementException exception = null;
			// Finding element using Selenium
			if(by != null)
			{
				try
				{
					element = searchContext.findElement(by);
				}
				catch (NoSuchElementException e) 
				{
					exception = e;
					LOGGER.error("Unable to find element: " + e.getMessage());
				}
			}
			
			// Finding element using AI tool
			if(element == null && AliceRecognition.INSTANCE.isEnabled())
			{
				element = findElementByAI(driver, aiLabel, aiCaption);
			}
			// If no luck throw general NoSuchElementException
			if(element == null)
			{
				throw exception != null ? exception : new NoSuchElementException("Unable to find element by Selenium/AI");
			}
		} 
		else
		{
			if (driver instanceof IOSDriver)
			{
				element = driver.findElement(MobileBy.iOSNsPredicateString(getLocator(by)));
			} 
			else if (driver instanceof AndroidDriver)
			{
				element = ((AndroidDriver) driver).findElementByAndroidUIAutomator(getLocator(by));
			} 
			else
			{
				throw new RuntimeException("Unable to to detect valid driver for searching " + by.toString());
			}
		}

		if (shouldCache)
		{
			cachedElement = element;
		}
		return element;
	}

	/**
	 * Find the element list.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<WebElement> findElements()
	{
		if (cachedElementList != null && shouldCache)
		{
			return cachedElementList;
		}

		List<WebElement> elements = null;
		if (!isPredicate)
		{
			elements = searchContext.findElements(by);
		} else
		{
			WebDriver drv = DriverPool.getDriver();
			if (drv instanceof IOSDriver)
			{
				elements = drv.findElements(MobileBy.iOSNsPredicateString(getLocator(by)));
			} else if (drv instanceof AndroidDriver)
			{
				elements = ((AndroidDriver) drv).findElementsByAndroidUIAutomator(getLocator(by));
			} else
			{
				throw new RuntimeException("Unable to to detect valid driver for searching " + by.toString());
			}
		}

		if (shouldCache)
		{
			cachedElementList = elements;
		}

		return elements;
	}
	
	private WebElement findElementByAI(WebDriver drv, Label label, String caption)
	{
		WebElement element = null;
		
		RecognitionMetaType result = AliceRecognition.INSTANCE.recognize(aiLabel, aiCaption, drv);
		
		if(result != null)
		{
			int x = (result.getTopleft().getX() + result.getBottomright().getX()) / 2;
			int y = (result.getTopleft().getY() + result.getBottomright().getY()) / 2;
			((JavascriptExecutor) drv).executeScript("window.scrollTo(0, 0);");
			element = (WebElement) ((JavascriptExecutor) drv).executeScript("return document.elementFromPoint(arguments[0], arguments[1])", x, y);
			highligh(drv, result, element);
		}
		else
		{
			throw new NoSuchElementException("Unable to find element by AI label: " + aiLabel + ", caption: " + aiCaption);
		}
		return element;
	}

	private String getLocator(By by)
	{
		String locator = by.toString();

		if (locator.startsWith("id="))
		{
			return StringUtils.remove(locator, "id=");
		} else if (locator.startsWith("name="))
		{
			return StringUtils.remove(locator, "name=");
		} else if (locator.startsWith("xpath="))
		{
			return StringUtils.remove(locator, "xpath=");
		} else if (locator.startsWith("linkText="))
		{
			return StringUtils.remove(locator, "linkText=");
		} else if (locator.startsWith("partialLinkText="))
		{
			return StringUtils.remove(locator, "partialLinkText=");
		} else if (locator.startsWith("cssSelector="))
		{
			return StringUtils.remove(locator, "cssSelector=");
		} else if (locator.startsWith("css="))
		{
			return StringUtils.remove(locator, "css=");
		} else if (locator.startsWith("tagName="))
		{
			return StringUtils.remove(locator, "tagName=");
		} else if (locator.startsWith("className="))
		{
			return StringUtils.remove(locator, "className=");
		} else if (locator.startsWith("By.id: "))
		{
			return StringUtils.remove(locator, "By.id: ");
		} else if (locator.startsWith("By.name: "))
		{
			return StringUtils.remove(locator, "By.name: ");
		} else if (locator.startsWith("By.xpath: "))
		{
			return StringUtils.remove(locator, "By.xpath: ");
		} else if (locator.startsWith("By.linkText: "))
		{
			return StringUtils.remove(locator, "By.linkText: ");
		} else if (locator.startsWith("By.partialLinkText: "))
		{
			return StringUtils.remove(locator, "By.partialLinkText: ");
		} else if (locator.startsWith("By.css: "))
		{
			return StringUtils.remove(locator, "By.css: ");
		} else if (locator.startsWith("By.cssSelector: "))
		{
			return StringUtils.remove(locator, "By.cssSelector: ");
		} else if (locator.startsWith("By.className: "))
		{
			return StringUtils.remove(locator, "By.className: ");
		} else if (locator.startsWith("By.tagName: "))
		{
			return StringUtils.remove(locator, "By.tagName: ");
		}

		throw new RuntimeException(String.format("Unable to generate By using locator: '%s'!", locator));
	}
	
	private void highligh(WebDriver drv, RecognitionMetaType recognition, WebElement element)
	{
		final int tlX = recognition.getTopleft().getX();
		final int tlY = recognition.getTopleft().getY();
		
		final int brX = recognition.getBottomright().getX();
		final int brY = recognition.getBottomright().getY();
		
		final int widht = recognition.getBottomright().getX() - recognition.getTopleft().getX();
		final int height = recognition.getBottomright().getY() - recognition.getTopleft().getY();
		
		try
		{
			final long timeout = 2500;
			
			String js =  
					"var x = " + tlX + ";" +
					"var y = " + tlY + ";" +
					"var x2 = " + brX + ";" +
					"var y2 = " + brY + ";" +
					"var width = " + widht + ";" +
					"var height = " + height + ";" +
					"var canvas = document.createElement('canvas');" +
					"canvas.style.width='100%';" +
					"canvas.style.height='100%';" +
					"canvas.width = window.innerWidth;" +
					"canvas.height = window.innerHeight;" +
					"canvas.style.position='absolute';" +
					"canvas.style.left=0;" +
					"canvas.style.top=0;" +
					"canvas.style.zIndex=100000;" +
					"canvas.style.pointerEvents='none';" +
					"document.body.appendChild(canvas);" +
					"var context = canvas.getContext('2d');" +
					"context.fillStyle = 'red';" +
					"context.strokeStyle = 'red';" +
					"context.strokeRect(x, y, width, height);" +
					"context.beginPath();" +
					"context.arc((x + x2) / 2, (y + y2) / 2, 5, 0, Math.PI * 2, true);" +
					"context.moveTo(x, y);" +
				    "context.lineTo(x2, y2);" +
				    "context.moveTo(x, y2);" +
				    "context.lineTo(x2, y);" + 
				    "context.stroke();" +
				    "context.font = '20px Courier red';" +
				    "context.fillText('" + recognition.getLabel().toUpperCase()  + ": " + recognition.getCaption() + " " + recognition.getConfidence() * 100 + "%', x, y - 10);" + 
				    "arguments[0].style.border='3px solid green';" +
				    "setTimeout(function () { canvas.remove(); }, " + timeout + ");";
				((JavascriptExecutor) drv).executeScript(js, element);
				
				Thread.sleep(timeout);
		}
		catch (Exception e) 
		{
			LOGGER.error(e.getMessage());
		}
	}
}
