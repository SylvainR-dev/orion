import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';

import { OrganizationDetailsComponent } from './organization-details.component';
import { PersonService, Person } from '../person.service';
import { OrganizationService, Organization } from '../organization.service';

describe('OrganizationDetailsComponent', () => {
  let component: OrganizationDetailsComponent;
  let fixture: ComponentFixture<OrganizationDetailsComponent>;
  let personServiceSpy: jasmine.SpyObj<PersonService>;
  let organizationServiceSpy: jasmine.SpyObj<OrganizationService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const makePerson = (overrides: Partial<Person> = {}): Person => ({
    id: 1,
    firstName: 'Jane',
    lastName: 'Doe',
    email: 'jane@doe.com',
    phone: '',
    bio: '',
    createdAt: new Date(),
    organizations: [],
    ...overrides,
  });

  const makeOrg = (overrides: Partial<Organization> = {}): Organization => ({
    id: 10,
    name: 'Orion',
    createdAt: new Date(),
    personIds: [],
    persons: [],
    ...overrides,
  });

  function makeActivatedRoute(orgId: string | null): Partial<ActivatedRoute> {
    return {
      snapshot: {
        paramMap: convertToParamMap(orgId !== null ? { orgId } : {})
      } as any
    };
  }

  function configure(orgIdParam: string | null) {
    personServiceSpy = jasmine.createSpyObj('PersonService', ['fetchById', 'save', 'deleteById']);
    organizationServiceSpy = jasmine.createSpyObj('OrganizationService', ['fetchById', 'save', 'deleteById']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    organizationServiceSpy.fetchById.and.resolveTo(makeOrg());
    organizationServiceSpy.save.and.resolveTo(makeOrg());
    organizationServiceSpy.deleteById.and.resolveTo();

    TestBed.configureTestingModule({
      imports: [OrganizationDetailsComponent],
      providers: [
        { provide: PersonService, useValue: personServiceSpy },
        { provide: OrganizationService, useValue: organizationServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: ActivatedRoute, useValue: makeActivatedRoute(orgIdParam) },
      ]
    });

    fixture = TestBed.createComponent(OrganizationDetailsComponent);
    component = fixture.componentInstance;
  }

  it('should create', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();
    expect(component).toBeTruthy();
  }));

  it('ngOnInit should set isNew when orgId param is "new"', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    expect(component.isNew).toBeTrue();
    expect(organizationServiceSpy.fetchById).not.toHaveBeenCalled();
  }));

  it('ngOnInit should fetch the organization when orgId param is numeric', fakeAsync(() => {
    configure('10');
    const org = makeOrg({ id: 10, persons: [makePerson()] });
    organizationServiceSpy.fetchById.and.resolveTo(org);

    fixture.detectChanges();
    tick();

    expect(organizationServiceSpy.fetchById).toHaveBeenCalledWith(10);
    expect(component.org).toEqual(org);
    expect(component.isNew).toBeFalse();
  }));

  it('ngOnInit should do nothing when orgId param is absent', fakeAsync(() => {
    configure(null);
    fixture.detectChanges();
    tick();

    expect(organizationServiceSpy.fetchById).not.toHaveBeenCalled();
    expect(component.isNew).toBeFalse();
  }));

  it('saveOrg should save and navigate when creating a new organization', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    const saved = makeOrg({ id: 42 });
    organizationServiceSpy.save.and.resolveTo(saved);

    component.saveOrg();
    tick();

    expect(organizationServiceSpy.save).toHaveBeenCalled();
    expect(component.org).toEqual(saved);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['organizations', 42]);
  }));

  it('saveOrg should save without navigating when updating an existing organization', fakeAsync(() => {
    configure('10');
    const existing = makeOrg({ id: 10 });
    organizationServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    organizationServiceSpy.save.and.resolveTo(existing);
    component.saveOrg();
    tick();

    expect(routerSpy.navigate).not.toHaveBeenCalled();
  }));

  it('deleteOrg should do nothing when organization has no id', fakeAsync(() => {
    configure('new');
    fixture.detectChanges();
    tick();

    component.deleteOrg();
    tick();

    expect(organizationServiceSpy.deleteById).not.toHaveBeenCalled();
    expect(routerSpy.navigate).not.toHaveBeenCalled();
  }));

  it('deleteOrg should delete and navigate home when organization has an id', fakeAsync(() => {
    configure('10');
    const existing = makeOrg({ id: 10 });
    organizationServiceSpy.fetchById.and.resolveTo(existing);
    fixture.detectChanges();
    tick();

    component.deleteOrg();
    tick();

    expect(organizationServiceSpy.deleteById).toHaveBeenCalledWith(10);
    expect(routerSpy.navigate).toHaveBeenCalledWith(['']);
  }));
});